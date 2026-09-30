import { Client, type StompSubscription } from "@stomp/stompjs"
import { getStoredToken } from "@/api/client"

/** http(s)://host:port/api -> ws(s)://host:port/ws */
function resolveWsUrl(): string {
  const apiUrl = (import.meta.env.VITE_API_URL as string | undefined) ?? "http://localhost:8080/api"
  return apiUrl.replace(/\/api\/?$/, "").replace(/^http/, "ws") + "/ws"
}

interface Listener {
  destination: string
  onMessage: () => void
  subscription: StompSubscription | null
}

const listeners = new Set<Listener>()
let client: Client | null = null

function ensureClient(): Client {
  if (client) return client
  const instance = new Client({
    brokerURL: resolveWsUrl(),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  })
  instance.beforeConnect = () => {
    instance.connectHeaders = { Authorization: `Bearer ${getStoredToken() ?? ""}` }
  }
  instance.onConnect = () => {
    for (const listener of listeners) {
      listener.subscription = instance.subscribe(listener.destination, () => listener.onMessage())
    }
  }
  instance.activate()
  client = instance
  return instance
}

/**
 * Subscribes to a STOMP destination; payloads are never read (Phase 7's events are just
 * "something changed" signals - see RealtimeEventService on the backend), so onMessage takes no
 * argument and the caller reacts by invalidating whatever TanStack Query cache it owns. Returns
 * an unsubscribe function for a useEffect cleanup.
 */
export function subscribeRealtime(destination: string, onMessage: () => void): () => void {
  const active = ensureClient()
  const listener: Listener = { destination, onMessage, subscription: null }
  listeners.add(listener)
  if (active.connected) {
    listener.subscription = active.subscribe(destination, () => listener.onMessage());
  }
  return () => {
    listener.subscription?.unsubscribe()
    listeners.delete(listener)
  }
}

/** Called on logout - closes the socket and drops every pending listener. */
export function disconnectRealtime(): void {
  listeners.clear()
  client?.deactivate()
  client = null
}
