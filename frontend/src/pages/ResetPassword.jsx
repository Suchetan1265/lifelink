import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { auth } from '../api/endpoints';
import { errorMessage } from '../api/client';
import AuthLayout from '../components/AuthLayout';

export default function ResetPassword() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get('token');

  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (password !== confirm) {
      setError('The two passwords do not match.');
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await auth.resetPassword(token, password);
      navigate('/login', { replace: true, state: { passwordReset: true } });
    } catch (resetError) {
      setError(errorMessage(resetError, 'Could not reset the password'));
    } finally {
      setSubmitting(false);
    }
  };

  if (!token) {
    return (
      <AuthLayout motto={false}>
        <h2>That link is incomplete</h2>
        <p className="sub">
          The reset link is missing its token. Open it straight from the message, or ask for a new
          one.
        </p>
        <Link className="button pill block" to="/forgot-password">
          Request a new link
        </Link>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout motto={false}>
      <h2>Choose a new password</h2>
      <p className="sub">This signs you out everywhere else.</p>

      <form onSubmit={handleSubmit}>
        <div className="field">
          <label className="field-label" htmlFor="password">
            New password
          </label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
            minLength={8}
            autoComplete="new-password"
            autoFocus
          />
          <span className="field-hint">At least 8 characters.</span>
        </div>

        <div className="field">
          <label className="field-label" htmlFor="confirm">
            Confirm it
          </label>
          <input
            id="confirm"
            type="password"
            value={confirm}
            onChange={(event) => setConfirm(event.target.value)}
            required
            autoComplete="new-password"
          />
        </div>

        {error && <p className="error">{error}</p>}

        <button type="submit" className="button pill block" disabled={submitting}>
          {submitting ? 'Saving…' : 'Set new password'}
        </button>
      </form>

      <p className="auth-alt">
        <Link to="/login">Back to sign in</Link>
      </p>
    </AuthLayout>
  );
}
