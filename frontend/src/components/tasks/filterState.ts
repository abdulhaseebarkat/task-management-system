export interface FilterValues {
  searchInput: string
  status: string
  priority: string
  categoryId: string
  assigneeId: string
  sort: string
}

export const DEFAULT_FILTERS: FilterValues = { searchInput: "", status: "", priority: "", categoryId: "", assigneeId: "", sort: "updated:desc" }
