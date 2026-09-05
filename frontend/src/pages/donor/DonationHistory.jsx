import { useQuery } from '@tanstack/react-query';
import { donors } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { Empty, ErrorNote, Loading } from '../../components/Spinner';

export default function DonationHistory() {
  const donations = useQuery({ queryKey: ['donor', 'donations'], queryFn: donors.donations });

  if (donations.isLoading) return <Loading />;
  if (donations.isError) return <ErrorNote>{errorMessage(donations.error)}</ErrorNote>;

  const total = donations.data.reduce((sum, donation) => sum + donation.units, 0);

  return (
    <>
      <h1>Your donations</h1>
      {donations.data.length === 0 ? (
        <Empty>No donations recorded yet.</Empty>
      ) : (
        <>
          <p className="muted">
            {donations.data.length} donation(s), {total} unit(s) in total. Thank you.
          </p>
          <table className="table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Units</th>
                <th>Request</th>
              </tr>
            </thead>
            <tbody>
              {donations.data.map((donation) => (
                <tr key={donation.id}>
                  <td>{new Date(donation.donatedAt).toLocaleDateString()}</td>
                  <td>{donation.units}</td>
                  <td>#{donation.requestId}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </>
  );
}
