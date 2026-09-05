import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { bloodBanks } from '../../api/endpoints';
import { errorMessage } from '../../api/client';
import { ErrorNote, Loading } from '../../components/Spinner';

export default function Inventory() {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState(null);

  const inventory = useQuery({ queryKey: ['bank', 'inventory'], queryFn: bloodBanks.inventory });

  // The server returns all eight groups, so the grid is whatever it sends.
  useEffect(() => {
    if (inventory.data) {
      setDraft(Object.fromEntries(inventory.data.map((row) => [row.bloodGroup, row.units])));
    }
  }, [inventory.data]);

  const save = useMutation({
    mutationFn: bloodBanks.updateInventory,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['bank'] }),
  });

  if (inventory.isLoading || !draft) return <Loading />;
  if (inventory.isError) return <ErrorNote>{errorMessage(inventory.error)}</ErrorNote>;

  const total = Object.values(draft).reduce((sum, units) => sum + Number(units || 0), 0);

  const handleSubmit = (event) => {
    event.preventDefault();
    save.mutate(
      Object.entries(draft).map(([bloodGroup, units]) => ({
        bloodGroup,
        units: Number(units),
      })),
    );
  };

  return (
    <>
      <h1>Inventory</h1>
      <p className="muted">{total} unit(s) on hand across all groups.</p>

      <form onSubmit={handleSubmit} className="card">
        <div className="inventory-grid">
          {inventory.data.map((row) => (
            <label key={row.bloodGroup}>
              {row.bloodGroup}
              <input
                type="number"
                min="0"
                value={draft[row.bloodGroup]}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, [row.bloodGroup]: event.target.value }))
                }
              />
            </label>
          ))}
        </div>

        {save.isError && <ErrorNote>{errorMessage(save.error)}</ErrorNote>}
        {save.isSuccess && !save.isPending && <p className="muted">Saved.</p>}

        <button type="submit" className="button" disabled={save.isPending}>
          {save.isPending ? 'Saving…' : 'Save inventory'}
        </button>
      </form>
    </>
  );
}
