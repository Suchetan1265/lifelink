import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { auth, meta } from '../api/endpoints';
import { errorMessage } from '../api/client';
import { homePathFor, useAuth } from '../auth/AuthContext';
import AuthLayout from '../components/AuthLayout';
import LocationPicker from '../components/LocationPicker';

const TABS = [
  { id: 'donor', label: 'Donor' },
  { id: 'hospital', label: 'Hospital' },
  { id: 'bloodbank', label: 'Blood bank' },
];

// Somewhere sensible to open the map before the browser offers a real position.
const DEFAULT_POINT = { lat: 12.9716, lng: 77.5946 };

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
};

export default function Register() {
  const { user, loading, startSession } = useAuth();
  const navigate = useNavigate();

  const [tab, setTab] = useState('donor');
  const [form, setForm] = useState(BLANK);
  const [point, setPoint] = useState(DEFAULT_POINT);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const { data: bloodGroups = [] } = useQuery({
    queryKey: ['blood-groups'],
    queryFn: meta.bloodGroups,
    staleTime: Infinity,
  });

  if (loading) return <p className="muted center">Loading…</p>;
  if (user) return <Navigate to={homePathFor(user.role)} replace />;

  const set = (field) => (event) =>
    setForm((current) => ({ ...current, [field]: event.target.value }));

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    const account = { email: form.email, phone: form.phone, password: form.password };
    const location = { lat: point.lat, lng: point.lng };

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

  const isDonor = tab === 'donor';

  return (
    <AuthLayout motto={false}>
      <h2>Create an account</h2>
      <p className="sub">Tell us who you are and where you are.</p>

      <div className="tabs" role="tablist">
        {TABS.map((option) => (
          <button
            key={option.id}
            type="button"
            role="tab"
            aria-selected={tab === option.id}
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

      {!isDonor && (
        <p className="field-hint">
          Hospitals and blood banks stay pending until an administrator verifies the registration.
          You can sign in straight away, but you cannot raise requests or hold stock until then.
        </p>
      )}

      <form onSubmit={handleSubmit}>
        <label>
          Email
          <input type="email" value={form.email} onChange={set('email')} required />
        </label>
        <label>
          Phone
          <input value={form.phone} onChange={set('phone')} placeholder="+91 90000 00000" />
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
          <span className="field-hint">At least 8 characters.</span>
        </label>

        {isDonor ? (
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
          </>
        ) : (
          <>
            <label>
              {tab === 'hospital' ? 'Hospital name' : 'Blood bank name'}
              <input value={form.name} onChange={set('name')} required />
            </label>
            {tab === 'hospital' && (
              <label>
                Licence number
                <input value={form.licenseNo} onChange={set('licenseNo')} required />
                <span className="field-hint">An administrator checks this before approving you.</span>
              </label>
            )}
            <label>
              Address
              <input value={form.address} onChange={set('address')} required />
            </label>
          </>
        )}

        <LocationPicker
          value={point}
          onChange={setPoint}
          label={isDonor ? 'Where you are' : 'Where you are based'}
        />

        {isDonor && (
          <label>
            How far will you travel to donate? <strong>{form.radiusKm} km</strong>
            <input
              type="range"
              min="1"
              max="100"
              value={form.radiusKm}
              onChange={set('radiusKm')}
            />
            <span className="field-hint">
              You will only ever be shown requests inside this radius.
            </span>
          </label>
        )}

        {error && <p className="error">{error}</p>}

        <button type="submit" className="button pill block" disabled={submitting}>
          {submitting ? 'Creating…' : 'Create account'}
        </button>
      </form>

      <p className="auth-alt">
        Already registered? <Link to="/login">Sign in</Link>
      </p>
    </AuthLayout>
  );
}
