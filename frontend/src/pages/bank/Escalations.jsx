import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { bloodBanks } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import StatusChip from '../../components/StatusChip';
import { Empty, ErrorNote, Loading } from '../../components/Spinner';

export default function Escalations() {
  const queryClient = useQueryClient();
  const [units, setUnits] = useState({});

  const escalations = useQuery({
    queryKey: ['bank', 'escalations'],
    queryFn: bloodBanks.escalations,
    // Escalations appear when the Quartz job runs, not in response to anything
    // the bank does, so poll.
    refetchInterval: 20_000,
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['bank'] });

  const accept = useMutation({ mutationFn: bloodBanks.acceptEscalation, onSuccess: refresh });
  const fulfill = useMutation({
    mutationFn: ({ requestId, unitsToRelease }) =>
      bloodBanks.fulfillEscalation(requestId, unitsToRelease),
    onSuccess: refresh,
  });

  if (escalations.isLoading) return <Loading />;
  if (escalations.isError) return <ErrorNote>{errorMessage(escalations.error)}</ErrorNote>;

  const actionError = accept.error || fulfill.error;

  return (
    <>
      <h1>Escalated requests near you</h1>
      <p className="muted">
        Requests within 50 km that no donor confirmed before their urgency deadline.
      </p>

      {actionError && <ErrorNote>{errorMessage(actionError)}</ErrorNote>}

      {escalations.data.length === 0 ? (
        <Empty>Nothing escalated right now.</Empty>
      ) : (
        <div className="grid">
          {escalations.data.map((escalation) => {
            const short = escalation.unitsInStock < escalation.units;
            const requested = units[escalation.requestId] ?? escalation.units;
            return (
              <article key={escalation.requestId} className="card">
                <header className="card-header">
                  <strong>{escalation.hospitalName}</strong>
                  <StatusChip value={escalation.urgency} />
                </header>
                <p>
                  {escalation.units} unit(s) of <strong>{escalation.bloodGroup}</strong>, about{' '}
                  {escalation.distanceKm} km away.
                </p>
                <p className={short ? 'error' : 'muted'}>
                  You hold {escalation.unitsInStock} unit(s) of {escalation.bloodGroup}
                  {short && ' — not enough to cover this on its own.'}
                </p>
                <p className="muted">{escalation.hospitalAddress}</p>
                <p className="muted">
                  Needed by {new Date(escalation.neededBy).toLocaleString()}
                </p>
                {escalation.notes && <p>{escalation.notes}</p>}

                <div className="row">
                  <button
                    type="button"
                    className="button"
                    disabled={accept.isPending}
                    onClick={() => accept.mutate(escalation.requestId)}
                  >
                    Accept
                  </button>
                  <label className="inline">
                    Units
                    <input
                      type="number"
                      min="1"
                      value={requested}
                      onChange={(event) =>
                        setUnits((current) => ({
                          ...current,
                          [escalation.requestId]: event.target.value,
                        }))
                      }
                    />
                  </label>
                  <button
                    type="button"
                    className="button ghost"
                    disabled={fulfill.isPending}
                    onClick={() =>
                      fulfill.mutate({
                        requestId: escalation.requestId,
                        unitsToRelease: Number(requested),
                      })
                    }
                  >
                    Release stock
                  </button>
                </div>
                <p className="muted">Accept first — only the bank that accepted can release stock.</p>
              </article>
            );
          })}
        </div>
      )}
    </>
  );
}
