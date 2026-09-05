import { useState } from 'react';
import { Link } from 'react-router-dom';
import { auth } from '../api/endpoints';
import { errorMessage } from '../api/client';
import AuthLayout from '../components/AuthLayout';

export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await auth.forgotPassword(email);
      setSent(true);
    } catch (resetError) {
      setError(errorMessage(resetError, 'Could not send the reset link'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout motto={false}>
      <h2>Reset your password</h2>

      {sent ? (
        <>
          <p className="sub">
            If <strong>{email}</strong> has an account, a link to choose a new password is on its
            way. It works once and expires in 30 minutes.
          </p>
          <p className="demo-hint">
            Nothing arrived? Check the address, or look in your in-app notifications once you are
            signed in.
          </p>
        </>
      ) : (
        <>
          <p className="sub">Tell us your email and we will send you a link.</p>
          <form onSubmit={handleSubmit}>
            <div className="field">
              <label className="field-label" htmlFor="email">
                Email
              </label>
              <span className="input-wrap">
                <input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="you@example.com"
                  required
                  autoFocus
                  style={{ paddingLeft: '0.8rem' }}
                />
              </span>
            </div>

            {error && <p className="error">{error}</p>}

            <button type="submit" className="button pill block" disabled={submitting}>
              {submitting ? 'Sending…' : 'Send reset link'}
            </button>
          </form>
        </>
      )}

      <p className="auth-alt">
        <Link to="/login">Back to sign in</Link>
      </p>
    </AuthLayout>
  );
}
