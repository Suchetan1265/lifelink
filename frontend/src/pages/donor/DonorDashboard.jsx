import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { donors } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import StatusChip from '../../components/StatusChip';
import { Empty, ErrorNote, Loading } from '../../components/Spinner';

function EligibilityCard({ eligibility }) {
  if (!eligibility) return null;
  if (eligibility.eligible) {
    return (
      <div className="card stat">
        <h2>Eligible to donate</h2>
        <p className="muted">You are off cooldown and can be matched right now.</p>
      </div>
    );
  }
  return (
    <div className="card stat">
      <h2>{eligibility.daysRemaining} days to go</h2>
      <p className="muted">
        You can donate again on {eligibility.nextEligibleDate}. Until then you are held
        back from matching.
      </p>
    </div>
  );
}

export default function DonorDashboard() {
  const queryClient = useQueryClient();

  const profile = useQuery({ queryKey: ['donor', 'profile'], queryFn: donors.profile });
  const eligibility = useQuery({ queryKey: ['donor', 'eligibility'], queryFn: donors.eligibility });
  const matches = useQuery({
    queryKey: ['donor', 'matches'],
    queryFn: donors.matches,
    // New matches arrive from the matching engine, so poll rather than making
    // people refresh while they are waiting to be called.
    refetchInterval: 10_000,
  });

  const refreshAll = () => {
    queryClient.invalidateQueries({ queryKey: ['donor'] });
    queryClient.invalidateQueries({ queryKey: ['notifications'] });
  };

  const toggleAvailability = useMutation({
    mutationFn: donors.setAvailability,
    onSuccess: refreshAll,
  });
  const respond = useMutation({
    mutationFn: ({ matchId, action }) =>
      action === 'accept' ? donors.acceptMatch(matchId) : donors.declineMatch(matchId),
    onSuccess: refreshAll,
  });

  if (profile.isLoading) return <Loading />;
  if (profile.isError) return <ErrorNote>{errorMessage(profile.error)}</ErrorNote>;

  const available = profile.data.available;
  const pending = (matches.data ?? []).filter((match) => match.matchStatus === 'NOTIFIED');
  const answered = (matches.data ?? []).filter((match) => match.matchStatus !== 'NOTIFIED');

  return (
    <>
      <h1>Hello, {profile.data.fullName}</h1>

      <section className="grid">
        <div className="card stat">
          <h2>{profile.data.bloodGroup}</h2>
          <p className="muted">
            {profile.data.city} · willing to travel {profile.data.radiusKm} km
          </p>
        </div>

        <EligibilityCard eligibility={eligibility.data} />

        <div className="card stat">
          <h2>{available ? 'Available' : 'Not available'}</h2>
          <p className="muted">
            {available
              ? 'You will be notified about nearby requests.'
              : 'You are out of matching until you turn this back on.'}
          </p>
          <button
            type="button"
            className={available ? 'button ghost' : 'button'}
            disabled={toggleAvailability.isPending}
            onClick={() => toggleAvailability.mutate(!available)}
          >
            {available ? 'Go unavailable' : 'I can donate now'}
          </button>
        </div>
      </section>

      {respond.isError && <ErrorNote>{errorMessage(respond.error)}</ErrorNote>}

      <h2>Requests waiting on you</h2>
      {matches.isLoading && <Loading />}
      {!matches.isLoading && pending.length === 0 && (
        <Empty>Nothing right now. You will be notified when a nearby hospital needs your group.</Empty>
      )}

      <div className="grid">
        {pending.map((match) => (
          <article key={match.matchId} className="card">
            <header className="card-header">
              <strong>{match.hospitalName}</strong>
              <StatusChip value={match.urgency} />
            </header>
            <p>
              {match.units} unit(s) of <strong>{match.bloodGroup}</strong>, about{' '}
              {match.distanceKm} km away.
            </p>
            <p className="muted">{match.hospitalAddress}</p>
            <p className="muted">Needed by {new Date(match.neededBy).toLocaleString()}</p>
            <div className="row">
              <button
                type="button"
                className="button"
                disabled={respond.isPending}
                onClick={() => respond.mutate({ matchId: match.matchId, action: 'accept' })}
              >
                Accept
              </button>
              <button
                type="button"
                className="button ghost"
                disabled={respond.isPending}
                onClick={() => respond.mutate({ matchId: match.matchId, action: 'decline' })}
              >
                Decline
              </button>
            </div>
          </article>
        ))}
      </div>

      {answered.length > 0 && (
        <>
          <h2>Already answered</h2>
          <table className="table">
            <thead>
              <tr>
                <th>Hospital</th>
                <th>Group</th>
                <th>Your answer</th>
                <th>Request</th>
                <th>Notified</th>
              </tr>
            </thead>
            <tbody>
              {answered.map((match) => (
                <tr key={match.matchId}>
                  <td>{match.hospitalName}</td>
                  <td>{match.bloodGroup}</td>
                  <td>
                    <StatusChip value={match.matchStatus} />
                  </td>
                  <td>
                    <StatusChip value={match.requestStatus} />
                  </td>
                  <td>{new Date(match.notifiedAt).toLocaleDateString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </>
  );
}
