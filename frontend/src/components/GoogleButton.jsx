import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { meta } from '../api/endpoints';

const SCRIPT_ID = 'google-identity-services';

function loadGoogleScript() {
  if (document.getElementById(SCRIPT_ID)) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.id = SCRIPT_ID;
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.onload = resolve;
    script.onerror = () => reject(new Error('Could not load Google sign-in'));
    document.head.appendChild(script);
  });
}

/**
 * Renders Google's own button, and only when the server says a client id is
 * configured — a sign-in button that cannot work is worse than none at all.
 */
export default function GoogleButton({ onCredential, disabled }) {
  const target = useRef(null);
  const [failed, setFailed] = useState(false);

  const { data: config } = useQuery({
    queryKey: ['auth-config'],
    queryFn: meta.authConfig,
    staleTime: Infinity,
  });

  const clientId = config?.googleClientId;

  useEffect(() => {
    if (!clientId || !target.current) return undefined;
    let cancelled = false;

    loadGoogleScript()
      .then(() => {
        if (cancelled || !window.google || !target.current) return;
        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: (response) => onCredential(response.credential),
        });
        window.google.accounts.id.renderButton(target.current, {
          theme: 'outline',
          size: 'large',
          width: 320,
          text: 'continue_with',
        });
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });

    return () => {
      cancelled = true;
    };
  }, [clientId, onCredential]);

  if (!clientId) return null;
  if (failed) {
    return <p className="field-hint">Google sign-in is unavailable right now. Use your password.</p>;
  }

  return (
    <>
      <div className="or-divider">
        <span>or</span>
      </div>
      <div ref={target} className="google-button" aria-disabled={disabled} />
    </>
  );
}
