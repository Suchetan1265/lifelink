import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { requests } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import StatusChip from '../../components/StatusChip';
import { ErrorNote, Loading } from '../../components/Spinner';

const STATUSES = ['RAISED', 'MATCHED', 'CONFIRMED', 'ESCALATED', 'FULFILLED', 'EXPIRED', 'CANCELLED'];

export default function RequestList() {
  const navigate = useNavigate();
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);

  const list = useQuery({
    queryKey: ['requests', { status, page }],
    queryFn: () => requests.list({ status: status || undefined, page, size: 20 }),
    // Donor responses land asynchronously, so keep the table moving.
    refetchInterval: 15_000,
  });

  if (list.isLoading) return <Loading />;
  if (list.isError) return <ErrorNote>{errorMessage(list.error)}</ErrorNote>;

  const open = (id) => navigate(`/hospital/requests/${id}`);

  return (
    <>
      <div className="page-header">
        <h1>Your requests</h1>
        <Link className="button" to="/hospital/requests/new">
          Raise request
        </Link>
      </div>
      <p className="page-intro">Select a row to see donor responses and record a donation.</p>

      <div className="row" style={{ marginTop: 0, marginBottom: '1rem' }}>
        <label className="inline">
          Status
          <select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <option value="">All</option>
            {STATUSES.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>
      </div>

      {list.data.content.length === 0 ? (
        <div className="empty">
          <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
            <rect x="3" y="4" width="18" height="16" rx="2" />
            <path d="M3 10h18M8 4v16" strokeLinecap="round" />
          </svg>
          <p>
            {status
              ? `No requests with status ${status}.`
              : 'No requests yet. Raise one and nearby donors are notified immediately.'}
          </p>
        </div>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Request</th>
                <th>Group</th>
                <th>Units</th>
                <th>Urgency</th>
                <th>Needed by</th>
                <th>Status</th>
                <th aria-label="Open" />
              </tr>
            </thead>
            <tbody>
              {list.data.content.map((request) => (
                <tr
                  key={request.id}
                  className="clickable"
                  tabIndex={0}
                  role="link"
                  aria-label={`Request ${request.id}, ${request.bloodGroup}, ${request.status}`}
                  onClick={() => open(request.id)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      open(request.id);
                    }
                  }}
                >
                  <td className="id">#{request.id}</td>
                  <td>
                    <strong>{request.bloodGroup}</strong>
                  </td>
                  <td className="num">{request.units}</td>
                  <td>
                    <StatusChip value={request.urgency} />
                  </td>
                  <td>{new Date(request.neededBy).toLocaleString()}</td>
                  <td>
                    <StatusChip value={request.status} />
                  </td>
                  <td className="row-arrow" aria-hidden="true">
                    &rsaquo;
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {list.data.totalPages > 1 && (
        <div className="row">
          <button
            type="button"
            className="button ghost small"
            disabled={page === 0}
            onClick={() => setPage((current) => current - 1)}
          >
            Previous
          </button>
          <span className="muted">
            Page {page + 1} of {list.data.totalPages}
          </span>
          <button
            type="button"
            className="button ghost small"
            disabled={page + 1 >= list.data.totalPages}
            onClick={() => setPage((current) => current + 1)}
          >
            Next
          </button>
        </div>
      )}
    </>
  );
}
