import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { requests } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import StatusChip from '../../components/StatusChip';
import { Empty, ErrorNote, Loading } from '../../components/Spinner';

const STATUSES = ['RAISED', 'MATCHED', 'CONFIRMED', 'ESCALATED', 'FULFILLED', 'EXPIRED', 'CANCELLED'];

export default function RequestList() {
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

  return (
    <>
      <div className="page-header">
        <h1>Your requests</h1>
        <Link className="button" to="/hospital/requests/new">
          Raise request
        </Link>
      </div>

      <label className="inline">
        Filter
        <select
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(0);
          }}
        >
          <option value="">All statuses</option>
          {STATUSES.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </label>

      {list.data.content.length === 0 ? (
        <Empty>No requests yet.</Empty>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>#</th>
              <th>Group</th>
              <th>Units</th>
              <th>Urgency</th>
              <th>Needed by</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {list.data.content.map((request) => (
              <tr key={request.id}>
                <td>
                  <Link to={`/hospital/requests/${request.id}`}>#{request.id}</Link>
                </td>
                <td>{request.bloodGroup}</td>
                <td>{request.units}</td>
                <td>
                  <StatusChip value={request.urgency} />
                </td>
                <td>{new Date(request.neededBy).toLocaleString()}</td>
                <td>
                  <StatusChip value={request.status} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {list.data.totalPages > 1 && (
        <div className="row">
          <button
            type="button"
            className="button ghost"
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
            className="button ghost"
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
