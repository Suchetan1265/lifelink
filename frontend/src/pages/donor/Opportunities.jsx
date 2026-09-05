import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { donors } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import StatusChip from '../../components/StatusChip';
import { Empty, ErrorNote, Loading } from '../../components/Spinner';

export default function Opportunities() {
  const queryClient = useQueryClient();

  const opportunities = useQuery({
    queryKey: ['donor', 'opportunities'],
    queryFn: donors.opportunities,
    refetchInterval: 20_000,
  });
  const eligibility = useQuery({ queryKey: ['donor', 'eligibility'], queryFn: donors.eligibility });
  const matches = useQuery({ queryKey: ['donor', 'matches'], queryFn: donors.matches });

  const volunteer = useMutation({
    mutationFn: donors.volunteer,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['donor'] });
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  if (opportunities.isLoading) return <Loading />;
  if (opportunities.isError) return <ErrorNote>{errorMessage(opportunities.error)}</ErrorNote>;

  const onCooldown = eligibility.data && !eligibility.data.eligible;
  // One live commitment at a time: you can only give blood once per visit.
  const committed = (matches.data ?? []).find(
    (match) => match.matchStatus === 'ACCEPTED' || match.matchStatus === 'CONFIRMED',
  );

  return (
    <>
      <h1>Where you can donate</h1>
      <p className="muted">
        Every open request near you that your blood group can help with, most urgent first.
      </p>

      {onCooldown && (
        <p className="error">
          You are on the 90-day cooldown until {eligibility.data.nextEligibleDate}, so you cannot
          volunteer yet.
        </p>
      )}
      {committed && !onCooldown && (
        <p className="muted">
          You are already committed to request #{committed.requestId} at {committed.hospitalName}.
          Finish or decline that one before taking another.
        </p>
      )}
      {volunteer.isError && <ErrorNote>{errorMessage(volunteer.error)}</ErrorNote>}

      {opportunities.data.length === 0 ? (
        <Empty>
          Nothing open near you right now. Widen the travel radius on your profile to see requests
          further away.
        </Empty>
      ) : (
        <div className="grid">
          {opportunities.data.map((opportunity) => (
            <article key={opportunity.requestId} className="card">
              <header className="card-header">
                <strong>{opportunity.hospitalName}</strong>
                <StatusChip value={opportunity.urgency} />
              </header>
              <p>
                {opportunity.units} unit(s) of <strong>{opportunity.bloodGroup}</strong>, about{' '}
                {opportunity.distanceKm} km away.
              </p>
              <p className="muted">{opportunity.hospitalAddress}</p>
              <p className="muted">
                Needed by {new Date(opportunity.neededBy).toLocaleString()}
              </p>
              {opportunity.notes && <p>{opportunity.notes}</p>}

              {opportunity.alreadyResponding ? (
                <p className="muted">Already on your matches list.</p>
              ) : (
                <button
                  type="button"
                  className="button"
                  disabled={volunteer.isPending || onCooldown || Boolean(committed)}
                  onClick={() => volunteer.mutate(opportunity.requestId)}
                >
                  I'll donate for this
                </button>
              )}
            </article>
          ))}
        </div>
      )}
    </>
  );
}
