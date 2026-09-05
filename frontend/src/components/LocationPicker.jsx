import { useEffect, useRef, useState } from 'react';
import { MapContainer, Marker, TileLayer, useMap, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';

const pin = L.icon({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  shadowSize: [41, 41],
});

const NOMINATIM = 'https://nominatim.openstreetmap.org';

/** Keeps the map centred on the value when it changes from outside the map. */
function Recentre({ lat, lng }) {
  const map = useMap();
  useEffect(() => {
    map.setView([lat, lng], Math.max(map.getZoom(), 14));
  }, [lat, lng, map]);
  return null;
}

function ClickToPlace({ onPick }) {
  useMapEvents({
    click(event) {
      onPick(event.latlng.lat, event.latlng.lng);
    },
  });
  return null;
}

/**
 * Picks a point on a map instead of asking anyone to type coordinates.
 *
 * Three ways in: the browser's location, a place-name search, or dropping the
 * pin by hand. The value is still a lat/lng pair, which is what the API takes —
 * it is only the entry that changes.
 */
export default function LocationPicker({ value, onChange, label = 'Location' }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [placeName, setPlaceName] = useState('');
  const [status, setStatus] = useState(null);
  const [busy, setBusy] = useState(false);
  const lastLookup = useRef('');

  const { lat, lng } = value;

  // Name the point so people can sanity-check the pin without reading numbers.
  useEffect(() => {
    const key = `${lat.toFixed(4)},${lng.toFixed(4)}`;
    if (lastLookup.current === key) return;
    lastLookup.current = key;

    let cancelled = false;
    fetch(`${NOMINATIM}/reverse?format=json&zoom=16&lat=${lat}&lon=${lng}`)
      .then((response) => (response.ok ? response.json() : null))
      .then((body) => {
        if (!cancelled && body?.display_name) setPlaceName(body.display_name);
      })
      .catch(() => {
        // A missing place name is cosmetic; the pin is still valid.
      });
    return () => {
      cancelled = true;
    };
  }, [lat, lng]);

  const useMyLocation = () => {
    if (!navigator.geolocation) {
      setStatus('This browser cannot share your location. Search or drop the pin instead.');
      return;
    }
    setBusy(true);
    setStatus(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setBusy(false);
        onChange({ lat: position.coords.latitude, lng: position.coords.longitude });
      },
      () => {
        setBusy(false);
        setStatus('Location permission was refused. Search for a place or drop the pin.');
      },
      { enableHighAccuracy: true, timeout: 10_000 },
    );
  };

  const search = async () => {
    if (!query.trim()) return;
    setBusy(true);
    setStatus(null);
    try {
      const response = await fetch(
        `${NOMINATIM}/search?format=json&limit=5&q=${encodeURIComponent(query)}`,
      );
      const found = await response.json();
      setResults(found);
      if (found.length === 0) setStatus('No places matched that search.');
    } catch {
      setStatus('Could not reach the place search. Drop the pin on the map instead.');
    } finally {
      setBusy(false);
    }
  };

  const choose = (place) => {
    setResults([]);
    setQuery('');
    onChange({ lat: Number(place.lat), lng: Number(place.lon) });
  };

  return (
    <div>
      <span className="field-label">{label}</span>
      <div className="locator">
        <div className="locator-bar">
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              // Enter must search, not submit the registration form around us.
              if (event.key === 'Enter') {
                event.preventDefault();
                search();
              }
            }}
            placeholder="Search a place, area or landmark"
            aria-label="Search for a place"
          />
          <button type="button" className="button ghost small" onClick={search} disabled={busy}>
            Search
          </button>
          <button type="button" className="button small" onClick={useMyLocation} disabled={busy}>
            Use my location
          </button>
        </div>

        {results.length > 0 && (
          <ul className="locator-results">
            {results.map((place) => (
              <li key={place.place_id}>
                <button type="button" onClick={() => choose(place)}>
                  {place.display_name}
                </button>
              </li>
            ))}
          </ul>
        )}

        <MapContainer center={[lat, lng]} zoom={14} className="locator-map" scrollWheelZoom={false}>
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <Marker position={[lat, lng]} icon={pin} />
          <ClickToPlace onPick={(nextLat, nextLng) => onChange({ lat: nextLat, lng: nextLng })} />
          <Recentre lat={lat} lng={lng} />
        </MapContainer>

        <div className="locator-foot">
          <span>{placeName || 'Click the map to move the pin'}</span>
          <span className="coords">
            {lat.toFixed(4)}, {lng.toFixed(4)}
          </span>
        </div>
      </div>
      {status && <p className="field-hint error">{status}</p>}
    </div>
  );
}
