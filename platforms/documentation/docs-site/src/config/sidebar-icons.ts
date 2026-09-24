/**
 * Sidebar icons, Cloudflare-docs style.
 *
 * Map a sidebar entry's label (exactly as it appears in sidebar-structure.json)
 * to a Starlight built-in icon name. Entries not listed here render without an
 * icon — top-level section headers (GRADLE USER MANUAL, …) should stay unlisted.
 *
 * Available names: https://starlight.astro.build/reference/icons/
 * (e.g. rocket, star, open-book, pencil, puzzle, setting, document,
 *  cloud-download, list-format, random, laptop, information, …)
 */
export const sidebarIcons: Record<string, string> = {
  "Getting Started": "rocket",
  "Installing Gradle": "cloud-download",
  "Upgrading Gradle": "up-arrow",
  "Migrating to Gradle": "random",
  "Learning Gradle Basics": "open-book",
  "Licenses": "document",
  "Writing Build Scripts": "pencil",
  "Creating Plugins": "puzzle",
  "Best Practices": "approve-check-circle",
  "Beginner Tutorial": "star",
  "Intermediate Tutorial": "star",
  "Advanced Tutorial": "star",
};
