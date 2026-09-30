export interface Notification {
  id: number
  type: string
  title: string
  message: string | null
  taskId: number | null
  read: boolean
  createdAt: string
}
