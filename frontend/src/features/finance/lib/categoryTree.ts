import type { Category } from '../types'

/**
 * Flattens the two level category tree into option rows for a native select.
 *
 * <p>A child is indented with a non-breaking space so the hierarchy survives inside an
 * {@code <option>}, which cannot hold markup.
 */
export function categoryOptions(categories: Category[]): { id: string; label: string }[] {
  const options: { id: string; label: string }[] = []

  for (const parent of categories) {
    options.push({ id: parent.id, label: parent.name })
    for (const child of parent.children ?? []) {
      options.push({ id: child.id, label: `\u00A0\u00A0\u2514 ${child.name}` })
    }
  }
  return options
}

/** Every category in the tree as a flat list, for lookups by id. */
export function flattenCategories(categories: Category[]): Category[] {
  return categories.flatMap((category) => [category, ...(category.children ?? [])])
}
