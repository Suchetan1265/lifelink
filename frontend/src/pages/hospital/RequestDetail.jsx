import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { requests } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import StatusChip from '../../components/StatusChip';
import DonorMap from '../../components/DonorMap';
import { Empty, ErrorNote, Loading } from '../../components/Spinner';

const OPEN_STATUSES = ['RAISED', 'MATCHED', 'CONFIRMED', 'ESCALATED'];

function Timeline({ entries }) {
  if (!entries?.length) return <Empty>No history yet.</Empty>;
  return (
    <ol className="timeline">
      {entries.map((entry, index) => (
        <li key={`${entry.changedAt}-${index}`}>
          <div>
            {entry.fromStatus && (
              <>
                <StatusChip value={entry.fromStatus} /> <span className="muted">→</span>{' '}
              </>
            )}
            <StatusChip value={entry.toStatus} />
          </div>
          <time className="muted">{new Date(entry.changedAt).toLocaleString()}</time>
          {entry.reason && <p className="muted">{entry.reason}</p>}
        </li>
      ))}
    </ol>
  );
}

export default function RequestDetail() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const [fulfillUnits, setFulfillUnits] = useState(1);
  const [cancelReason, setCancelReason] = useState('');

  // Donor responses arrive asynchronously; polling is simpler than a socket
  // and 10s is well inside the window a hospital cares about.
  const pollWhileOpen = (query) =>
    OPEN_STATUSES.includes(query.state.data?.status) ? 10_000 : false;

  const request = useQuery({
    queryKey: ['request', id],
    queryFn: () => requests.detail(id),
    refetchInterval: pollWhileOpen,
  });
  const matches = useQuery({
    queryKey: ['request', id, 'matches'],
    queryFn: () => requests.matches(id),
    refetchInterval: 10_000,
  });
  const history = useQuery({
    queryKey: ['request', id, 'history'],
    queryFn: () => requests.history(id),
  });

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['request', id] });
    queryClient.invalidateQueries({ queryKey: ['requests'] });
  };

  const confirm = useMutation({ mutationFn: (matchId) => requests.confirm(id, matchId), onSuccess: refresh });
  const fulfill = useMutation({ mutationFn: (body) => requests.fulfill(id, body), onSuccess: refresh });
  const cancel = useMutation({ mutationFn: (reason) => requests.cancel(id, reason), onSuccess: refresh });

  if (request.isLoading) return <Loading />;
  if (request.isError) return <ErrorNote>{errorMessage(request.error)}</ErrorNote>;

  const detail = request.data;
  const rows = matches.data ?? [];
  const confirmedDonor = rows.find((match) => match.status === 'CONFIRMED');
  const isOpen = OPEN_STATUSES.includes(detail.status);
  const actionError = confirm.error || fulfill.error || cancel.error;

  return (
    <>
      <div className="page-header">
        <h1>
          Request #{detail.id} <StatusChip value={detail.status} />
        </h1>
        <Link className="button ghost" to="/hospital/requests">
          Back
        </Link>
      </div>

      <section className="grid">
        <div className="card stat">
          <h2>
            {detail.units} × {detail.bloodGroup}
          </h2>
          <p className="muted">
            <StatusChip value={detail.urgency} /> needed by{' '}
            {new Date(detail.neededBy).toLocaleString()}
          </p>
          {detail.notes && <p>{detail.notes}</p>}
        </div>

        <div className="card stat">
          <h2>{rows.length} donor(s) notified</h2>
          <p className="muted">
            {rows.filter((match) => match.status === 'ACCEPTED').length} accepted ·{' '}
            {rows.filter((match) => match.status === 'DECLINED').length} declined
          </p>
          {detail.acceptedBankName && (
            <p>
              Covered by <strong>{detail.acceptedBankName}</strong>
            </p>
          )}
        </div>
      </section>

      {actionError && <ErrorNote>{errorMessage(actionError)}</ErrorNote>}

      <h2>Donor responses</h2>
      {rows.length === 0 ? (
        <Empty>
          No donors matched. If nobody responds before the urgency deadline, this escalates to
          nearby blood banks automatically.
        </Empty>
      ) : (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Donor</th>
                <th>Group</th>
                <th>Distance</th>
                <th>Response</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((match) => (
                <tr key={match.matchId}>
                  <td>{match.donorName}</td>
                  <td>{match.bloodGroup}</td>
                  <td>{match.distanceKm} km</td>
                  <td>
                    <StatusChip value={match.status} />
                  </td>
                  <td>
                    {match.status === 'ACCEPTED' && detail.status === 'MATCHED' && (
                      <button
                        type="button"
                        className="button small"
                        disabled={confirm.isPending}
                        onClick={() => confirm.mutate(match.matchId)}
                      >
                        Confirm
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <DonorMap
            center={{ lat: detail.hospitalLat, lng: detail.hospitalLng, label: detail.hospitalName }}
            matches={rows}
          />
        </>
      )}

      {detail.status === 'CONFIRMED' && confirmedDonor && (
        <section className="card">
          <h2>Record the donation</h2>
          <p className="muted">
            {confirmedDonor.donorName} is confirmed. Recording this starts their 90-day cooldown.
          </p>
          <div className="row">
            <label className="inline">
              Units
              <input
                type="number"
                min="1"
                value={fulfillUnits}
                onChange={(event) => setFulfillUnits(event.target.value)}
              />
            </label>
            <button
              type="button"
              className="button"
              disabled={fulfill.isPending}
              onClick={() =>
                fulfill.mutate({
                  donorId: confirmedDonor.donorId,
                  bloodBankId: null,
                  units: Number(fulfillUnits),
                })
              }
            >
              Record donation
            </button>
          </div>
        </section>
      )}

      {isOpen && (
        <section className="card">
          <h2>Cancel this request</h2>
          <div className="row">
            <input
              value={cancelReason}
              onChange={(event) => setCancelReason(event.target.value)}
              placeholder="Reason (kept in the audit trail)"
            />
            <button
              type="button"
              className="button ghost"
              disabled={cancel.isPending || !cancelReason.trim()}
              onClick={() => cancel.mutate(cancelReason.trim())}
            >
              Cancel request
            </button>
          </div>
        </section>
      )}

      <h2>Timeline</h2>
      <Timeline entries={history.data} />
    </>
  );
}
