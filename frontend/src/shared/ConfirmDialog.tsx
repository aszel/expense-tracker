import { useEffect, useRef, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
  onCancel: () => void;
  busy: boolean;
}

export function ConfirmDialog({ children, onCancel, busy }: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current!;
    dialog.showModal();
    return () => dialog.close();
  }, []);
  return (
    <dialog className="confirm" ref={ref} aria-labelledby="delete-title" onCancel={event => {
      event.preventDefault();
      if (!busy) onCancel();
    }}>
      {children}
    </dialog>
  );
}
