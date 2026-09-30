import { apiClient } from "./client"

export interface CategoryManagement {
  id: number
  name: string
  active: boolean
}

/** Admin-only management view - includes inactive categories, unlike the plain /task-categories list used for dropdowns. */
export async function listCategoriesForManagement(): Promise<CategoryManagement[]> {
  const response = await apiClient.get<CategoryManagement[]>("/task-categories/manage")
  return response.data
}

export async function createCategory(name: string): Promise<CategoryManagement> {
  const response = await apiClient.post<CategoryManagement>("/task-categories", { name })
  return response.data
}

export async function renameCategory(id: number, name: string): Promise<CategoryManagement> {
  const response = await apiClient.put<CategoryManagement>(`/task-categories/${id}`, { name })
  return response.data
}

export async function setCategoryActive(id: number, active: boolean): Promise<CategoryManagement> {
  const response = await apiClient.put<CategoryManagement>(`/task-categories/${id}/status`, { active })
  return response.data
}
