import { useQuery } from '@tanstack/react-query';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { admin } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { ErrorNote, Loading } from '../../components/Spinner';

// One hue per outcome so the status chart reads at a glance: open work is blue,
// good endings green, bad endings red, and abandoned ones grey.
const STATUS_COLOR = {
  RAISED: '#3b82f6',
  MATCHED: '#6366f1',
  CONFIRMED: '#8b5cf6',
  FULFILLED: '#16a34a',
  ESCALATED: '#f59e0b',
  EXPIRED: '#dc2626',
  CANCELLED: '#9ca3af',
};

function StatTile({ label, value, hint }) {
  return (
    <div className="card stat">
      <h2>{value}</h2>
      <p>{label}</p>
      {hint && <p className="muted">{hint}</p>}
    </div>
  );
}

export default function Stats() {
  const stats = useQuery({ queryKey: ['admin', 'stats'], queryFn: admin.stats });

  if (stats.isLoading) return <Loading />;
  if (stats.isError) return <ErrorNote>{errorMessage(stats.error)}</ErrorNote>;

  const data = stats.data;
  const byStatus = Object.entries(data.requestsByStatus).map(([status, count]) => ({
    status,
    count,
  }));
  const byGroup = Object.entries(data.donorsByBloodGroup).map(([group, count]) => ({
    group,
    count,
  }));

  return (
    <>
      <h1>Platform stats</h1>
      <p className="muted">Cached for five minutes, so numbers can lag slightly.</p>

      <section className="grid">
        <StatTile label="Requests raised" value={data.totalRequests} />
        <StatTile
          label="Fulfilment rate"
          value={`${Math.round(data.fulfillmentRate * 100)}%`}
          hint="Fulfilled over all requests raised"
        />
        <StatTile
          label="Avg time to fulfil"
          value={data.avgHoursToFulfill == null ? '—' : `${data.avgHoursToFulfill.toFixed(1)}h`}
          hint={data.avgHoursToFulfill == null ? 'Nothing fulfilled yet' : 'From raise to donation'}
        />
        <StatTile
          label="Donors"
          value={data.totalDonors}
          hint={`${data.availableDonors} available right now`}
        />
        <StatTile
          label="Verified organisations"
          value={data.verifiedHospitals + data.verifiedBloodBanks}
          hint={`${data.verifiedHospitals} hospitals · ${data.verifiedBloodBanks} blood banks`}
        />
        <StatTile
          label="Awaiting verification"
          value={data.pendingVerifications}
          hint="Blocked until an admin reviews them"
        />
      </section>

      <h2>Requests by status</h2>
      <div className="chart">
        <ResponsiveContainer width="100%" height={280}>
          <BarChart data={byStatus}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="status" tick={{ fontSize: 12 }} />
            <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
            <Tooltip />
            <Bar dataKey="count" radius={[4, 4, 0, 0]}>
              {byStatus.map((entry) => (
                <Cell key={entry.status} fill={STATUS_COLOR[entry.status] ?? '#9ca3af'} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>

      <h2>Donors by blood group</h2>
      {byGroup.length === 0 ? (
        <p className="muted">No donors registered yet.</p>
      ) : (
        <div className="chart">
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={byGroup}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="group" tick={{ fontSize: 12 }} />
              <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
              <Tooltip />
              <Bar dataKey="count" fill="#dc2626" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      <h2>Donors by city</h2>
      <table className="table">
        <thead>
          <tr>
            <th>City</th>
            <th>Donors</th>
          </tr>
        </thead>
        <tbody>
          {Object.entries(data.donorsByCity).map(([city, count]) => (
            <tr key={city}>
              <td>{city}</td>
              <td>{count}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
