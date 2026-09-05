import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { donors, meta } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import AuthLayout from '../../components/AuthLayout';
import LocationPicker from '../../components/LocationPicker';

const DEFAULT_POINT = { lat: 12.9716, lng: 77.5946 };

/**
 * A Google sign-in proves who someone is but says nothing about their blood
 * group, where they are, or how far they will travel. This collects the rest
 * before the donor screens can mean anything.
 */
export default function CompleteProfile() {
  const { user, refresh } = useAuth();
  const navigate = useNavigate();

  const [fullName, setFullName] = useState('');
  const [bloodGroup, setBloodGroup] = useState('O+');
  const [city, setCity] = useState('');
  const [radiusKm, setRadiusKm] = useState(15);
  const [point, setPoint] = useState(DEFAULT_POINT);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const { data: bloodGroups = [] } = useQuery({
    queryKey: ['blood-groups'],
    queryFn: meta.bloodGroups,
    staleTime: Infinity,
  });

  // Anyone who already has a profile has no business here.
  if (user?.profileComplete) return <Navigate to="/donor" replace />;

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await donors.updateProfile({
        fullName,
        bloodGroup,
        city,
        radiusKm: Number(radiusKm),
        lat: point.lat,
        lng: point.lng,
      });
      await refresh();
      navigate('/donor', { replace: true });
    } catch (saveError) {
      setError(errorMessage(saveError, 'Could not save your details'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout motto={false}>
      <h2>Almost there</h2>
      <p className="sub">
        We know who you are. Tell us what you can give and where, and hospitals near you can start
        reaching you.
      </p>

      <form onSubmit={handleSubmit}>
        <div className="field">
          <label className="field-label" htmlFor="fullName">
            Full name
          </label>
          <input
            id="fullName"
            value={fullName}
            onChange={(event) => setFullName(event.target.value)}
            required
            autoFocus
          />
        </div>

        <div className="field">
          <label className="field-label" htmlFor="bloodGroup">
            Blood group
          </label>
          <select
            id="bloodGroup"
            value={bloodGroup}
            onChange={(event) => setBloodGroup(event.target.value)}
          >
            {bloodGroups.map((group) => (
              <option key={group.group} value={group.group}>
                {group.group}
              </option>
            ))}
          </select>
        </div>

        <div className="field">
          <label className="field-label" htmlFor="city">
            City
          </label>
          <input
            id="city"
            value={city}
            onChange={(event) => setCity(event.target.value)}
            required
          />
        </div>

        <LocationPicker value={point} onChange={setPoint} label="Where you are" />

        <div className="field">
          <label className="field-label" htmlFor="radius">
            How far will you travel? <strong>{radiusKm} km</strong>
          </label>
          <input
            id="radius"
            type="range"
            min="1"
            max="100"
            value={radiusKm}
            onChange={(event) => setRadiusKm(event.target.value)}
          />
          <span className="field-hint">You will only be shown requests inside this radius.</span>
        </div>

        {error && <p className="error">{error}</p>}

        <button type="submit" className="button pill block" disabled={submitting}>
          {submitting ? 'Saving…' : 'Finish setting up'}
        </button>
      </form>
    </AuthLayout>
  );
}
