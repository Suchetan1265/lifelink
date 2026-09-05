import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { auth, meta } from '../api/endpoints';
import { errorMessage } from '../api/client';
import { homePathFor, useAuth } from '../auth/AuthContext';

const TABS = [
  { id: 'donor', label: 'Donor' },
  { id: 'hospital', label: 'Hospital' },
  { id: 'bloodbank', label: 'Blood bank' },
];

// Bengaluru, so the form opens somewhere sensible rather than null island.
const DEFAULT_LAT = 12.9716;
const DEFAULT_LNG = 77.5946;

const BLANK = {
  email: '',
  phone: '',
  password: '',
  fullName: '',
  bloodGroup: 'O+',
  city: 'Bengaluru',
  radiusKm: 15,
  name: '',
  licenseNo: '',
  address: '',
  lat: DEFAULT_LAT,
  lng: DEFAULT_LNG,
};

export default function Register() {
  const { user, loading, startSession } = useAuth();
  const navigate = useNavigate();

  const [tab, setTab] = useState('donor');
  const [form, setForm] = useState(BLANK);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const { data: bloodGroups = [] } = useQuery({
    queryKey: ['blood-groups'],
    queryFn: meta.bloodGroups,
    staleTime: Infinity,
  });

  if (loading) return <p className="muted center">Loading…</p>;
  if (user) return <Navigate to={homePathFor(user.role)} replace />;

  const set = (field) => (event) => {
    const { value } = event.target;
    setForm((current) => ({ ...current, [field]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    const location = { lat: Number(form.lat), lng: Number(form.lng) };
    const account = { email: form.email, phone: form.phone, password: form.password };

    try {
      let tokens;
      if (tab === 'donor') {
        tokens = await auth.registerDonor({
          ...account,
          fullName: form.fullName,
          bloodGroup: form.bloodGroup,
          city: form.city,
          radiusKm: Number(form.radiusKm),
          ...location,
        });
      } else if (tab === 'hospital') {
        tokens = await auth.registerHospital({
          ...account,
          name: form.name,
          licenseNo: form.licenseNo,
          address: form.address,
          ...location,
        });
      } else {
        tokens = await auth.registerBloodBank({
          ...account,
          name: form.name,
          address: form.address,
          ...location,
        });
      }

      const profile = await startSession(tokens);
      navigate(homePathFor(profile.role), { replace: true });
    } catch (registerError) {
      setError(errorMessage(registerError, 'Could not create the account'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="card narrow">
      <h1>Create an account</h1>

      <div className="tabs">
        {TABS.map((option) => (
          <button
            key={option.id}
            type="button"
            className={tab === option.id ? 'tab active' : 'tab'}
            onClick={() => {
              setTab(option.id);
              setError(null);
            }}
          >
            {option.label}
          </button>
        ))}
      </div>

      {tab !== 'donor' && (
        <p className="muted">
          Hospitals and blood banks stay pending until an admin verifies the registration.
        </p>
      )}

      <form onSubmit={handleSubmit}>
        <label>
          Email
          <input type="email" value={form.email} onChange={set('email')} required />
        </label>
        <label>
          Phone
          <input value={form.phone} onChange={set('phone')} placeholder="+91-90000-00000" />
        </label>
        <label>
          Password
          <input
            type="password"
            value={form.password}
            onChange={set('password')}
            required
            minLength={8}
            autoComplete="new-password"
          />
        </label>

        {tab === 'donor' ? (
          <>
            <label>
              Full name
              <input value={form.fullName} onChange={set('fullName')} required />
            </label>
            <label>
              Blood group
              <select value={form.bloodGroup} onChange={set('bloodGroup')}>
                {bloodGroups.map((group) => (
                  <option key={group.group} value={group.group}>
                    {group.group}
                  </option>
                ))}
              </select>
            </label>
            <label>
              City
              <input value={form.city} onChange={set('city')} required />
            </label>
            <label>
              How far will you travel? ({form.radiusKm} km)
              <input
                type="range"
                min="1"
                max="100"
                value={form.radiusKm}
                onChange={set('radiusKm')}
              />
            </label>
          </>
        ) : (
          <>
            <label>
              Name
              <input value={form.name} onChange={set('name')} required />
            </label>
            {tab === 'hospital' && (
              <label>
                Licence number
                <input value={form.licenseNo} onChange={set('licenseNo')} required />
              </label>
            )}
            <label>
              Address
              <input value={form.address} onChange={set('address')} required />
            </label>
          </>
        )}

        <div className="row">
          <label>
            Latitude
            <input type="number" step="0.0001" value={form.lat} onChange={set('lat')} required />
          </label>
          <label>
            Longitude
            <input type="number" step="0.0001" value={form.lng} onChange={set('lng')} required />
          </label>
        </div>

        {error && <p className="error">{error}</p>}

        <button type="submit" className="button" disabled={submitting}>
          {submitting ? 'Creating…' : 'Create account'}
        </button>
      </form>

      <p className="muted">
        Already registered? <Link to="/login">Sign in</Link>
      </p>
    </div>
  );
}
