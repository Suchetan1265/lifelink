import { MapContainer, Marker, Popup, TileLayer } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

// Leaflet's default icon URLs are resolved relative to the CSS, which a bundler
// rewrites; pointing them at the packaged assets keeps the markers visible.
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';

const defaultIcon = L.icon({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
});

/**
 * Where the matched donors are relative to the hospital. OpenStreetMap tiles,
 * so there is no API key to manage.
 */
export default function DonorMap({ center, matches }) {
  const plottable = matches.filter((match) => match.donorLat != null && match.donorLng != null);

  if (!center?.lat || !center?.lng) {
    return null;
  }

  return (
    <MapContainer
      center={[center.lat, center.lng]}
      zoom={12}
      scrollWheelZoom={false}
      className="map"
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />

      <Marker position={[center.lat, center.lng]} icon={defaultIcon}>
        <Popup>{center.label ?? 'Hospital'}</Popup>
      </Marker>

      {plottable.map((match) => (
        <Marker key={match.matchId} position={[match.donorLat, match.donorLng]} icon={defaultIcon}>
          <Popup>
            <strong>{match.donorName}</strong>
            <br />
            {match.bloodGroup} · {match.distanceKm} km
            <br />
            {match.status}
          </Popup>
        </Marker>
      ))}
    </MapContainer>
  );
}
