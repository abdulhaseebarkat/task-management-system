import { useEffect, useRef } from "react"
import { subscribeRealtime } from "@/lib/realtime"

/** destination = null skips subscribing (e.g. no task selected yet). */
export function useRealtimeSubscription(destination: string | null, onMessage: () => void): void {
  const handlerRef = useRef(onMessage)
  handlerRef.current = onMessage

  useEffect(() => {
    if (!destination) return undefined
    return subscribeRealtime(destination, () => handlerRef.current())
  }, [destination])
}
