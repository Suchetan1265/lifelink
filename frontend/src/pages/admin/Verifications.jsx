import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { admin } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { Empty, ErrorNote, Loading } from '../../components/Spinner';

const TYPES = [
  { id: 'hospital', label: 'Hospitals' },
  { id: 'bloodbank', label: 'Blood banks' },
];

export default function Verifications() {
  const queryClient = useQueryClient();
  const [type, setType] = useState('hospital');
  const [reasons, setReasons] = useState({});

  const queue = useQuery({
    queryKey: ['admin', 'verifications', type],
    queryFn: () => admin.verifications(type),
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin'] });
  const approve = useMutation({ mutationFn: admin.approve, onSuccess: refresh });
  const reject = useMutation({
    mutationFn: ({ userId, reason }) => admin.reject(userId, reason),
    onSuccess: refresh,
  });

  const actionError = approve.error || reject.error;

  return (
    <>
      <h1>Pending verifications</h1>
      <p className="muted">
        Until these are approved they cannot raise requests or hold stock, so nothing else
        in the platform works for them.
      </p>

      <div className="tabs">
        {TYPES.map((option) => (
          <button
            key={option.id}
            type="button"
            className={type === option.id ? 'tab active' : 'tab'}
            onClick={() => setType(option.id)}
          >
            {option.label}
          </button>
        ))}
      </div>

      {actionError && <ErrorNote>{errorMessage(actionError)}</ErrorNote>}
      {queue.isLoading && <Loading />}
      {queue.isError && <ErrorNote>{errorMessage(queue.error)}</ErrorNote>}

      {queue.data?.length === 0 && <Empty>Nothing waiting for review.</Empty>}

      <div className="grid">
        {(queue.data ?? []).map((entry) => (
          <article key={entry.userId} className="card">
            <header className="card-header">
              <strong>{entry.name}</strong>
              <span className="muted">#{entry.userId}</span>
            </header>
            <p className="muted">{entry.email}</p>
            {entry.phone && <p className="muted">{entry.phone}</p>}
            <p>{entry.address}</p>
            {entry.licenseNo && (
              <p>
                Licence <code>{entry.licenseNo}</code>
              </p>
            )}
            <p className="muted">
              Registered {new Date(entry.registeredAt).toLocaleDateString()}
            </p>

            <div className="row">
              <button
                type="button"
                className="button"
                disabled={approve.isPending}
                onClick={() => approve.mutate(entry.userId)}
              >
                Approve
              </button>
            </div>
            <div className="row">
              <input
                value={reasons[entry.userId] ?? ''}
                onChange={(event) =>
                  setReasons((current) => ({ ...current, [entry.userId]: event.target.value }))
                }
                placeholder="Reason for rejection"
              />
              <button
                type="button"
                className="button ghost"
                disabled={reject.isPending}
                onClick={() =>
                  reject.mutate({ userId: entry.userId, reason: reasons[entry.userId] || null })
                }
              >
                Reject
              </button>
            </div>
          </article>
        ))}
      </div>
    </>
  );
}
