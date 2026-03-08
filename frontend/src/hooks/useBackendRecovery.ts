import { useEffect, useRef } from 'react'

export function useBackendRecovery(hasError: boolean, onRecovered: () => void) {
  const onRecoveredRef = useRef(onRecovered)
  onRecoveredRef.current = onRecovered

  useEffect(() => {
    if (!hasError) return
    const id = setInterval(async () => {
      try {
        const res = await fetch('/api/admin/health')
        if (res.ok) {
          onRecoveredRef.current()
        }
      } catch {
        // still down
      }
    }, 5000)
    return () => clearInterval(id)
  }, [hasError])
}
