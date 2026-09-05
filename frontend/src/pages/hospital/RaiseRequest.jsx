import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { meta, requests } from '../../api/endpoints';
import { errorMessage } from '../../api/client';

const URGENCIES = [
  { value: 'CRITICAL', label: 'Critical — escalates to blood banks after 2h' },
  { value: 'HIGH', label: 'High — escalates after 6h' },
  { value: 'NORMAL', label: 'Normal — escalates after 24h' },
];

/** Datetime-local wants a local "YYYY-MM-DDTHH:mm", not an ISO instant. */
function defaultNeededBy() {
  const inSixHours = new Date(Date.now() + 6 * 60 * 60 * 1000);
  const offsetMs = inSixHours.getTimezoneOffset() * 60 * 1000;
  return new Date(inSixHours.getTime() - offsetMs).toISOString().slice(0, 16);
}

export default function RaiseRequest() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [bloodGroup, setBloodGroup] = useState('O+');
  const [units, setUnits] = useState(2);
  const [urgency, setUrgency] = useState('HIGH');
  const [neededBy, setNeededBy] = useState(defaultNeededBy);
  const [notes, setNotes] = useState('');

  const { data: bloodGroups = [] } = useQuery({
    queryKey: ['blood-groups'],
    queryFn: meta.bloodGroups,
    staleTime: Infinity,
  });

  const create = useMutation({
    mutationFn: requests.create,
    onSuccess: (request) => {
      queryClient.invalidateQueries({ queryKey: ['requests'] });
      navigate(`/hospital/requests/${request.id}`);
    },
  });

  const compatible = bloodGroups.find((group) => group.group === bloodGroup)?.canReceiveFrom ?? [];

  const handleSubmit = (event) => {
    event.preventDefault();
    create.mutate({
      bloodGroup,
      units: Number(units),
      urgency,
      // The input is local time; the API takes an instant.
      neededBy: new Date(neededBy).toISOString(),
      notes: notes || null,
    });
  };

  return (
    <div className="card narrow">
      <h1>Raise a request</h1>
      <form onSubmit={handleSubmit}>
        <label>
          Blood group needed
          <select value={bloodGroup} onChange={(event) => setBloodGroup(event.target.value)}>
            {bloodGroups.map((group) => (
              <option key={group.group} value={group.group}>
                {group.group}
              </option>
            ))}
          </select>
        </label>
        {compatible.length > 0 && (
          <p className="muted">Donors who can give: {compatible.join(', ')}</p>
        )}

        <label>
          Units
          <input
            type="number"
            min="1"
            max="50"
            value={units}
            onChange={(event) => setUnits(event.target.value)}
            required
          />
        </label>

        <label>
          Urgency
          <select value={urgency} onChange={(event) => setUrgency(event.target.value)}>
            {URGENCIES.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        <label>
          Needed by
          <input
            type="datetime-local"
            value={neededBy}
            onChange={(event) => setNeededBy(event.target.value)}
            required
          />
        </label>

        <label>
          Notes
          <textarea
            rows="3"
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            placeholder="Ward, contact, anything the donor should know"
          />
        </label>

        {create.isError && <p className="error">{errorMessage(create.error)}</p>}

        <button type="submit" className="button" disabled={create.isPending}>
          {create.isPending ? 'Raising…' : 'Raise request'}
        </button>
      </form>
      <p className="muted">
        Raising a request notifies the nearest compatible donors straight away. There is a
        cap of 10 requests per hour.
      </p>
    </div>
  );
}
