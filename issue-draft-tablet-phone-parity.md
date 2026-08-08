# Issue draft — upstream `pingdotgg/t3code` bug report

> Instructions for filing (not part of the issue): use the **Bug report** form on
> https://github.com/pingdotgg/t3code/issues/new/choose. The form fields below map
> 1:1 onto the template. Tick both "Before submitting" checkboxes, and attach the
> phone + tablet screenshots in the upload field. Delete this block before/while filing.

---

**Title:** `[Bug]: Android tablet split view lacks new-task and sidebar controls that exist on phone and iPad`

**Area:** `apps/mobile`

## Steps to reproduce

1. Install the Android app on a tablet (window ≥ 720dp wide and ≥ 600dp tall, so the split layout activates).
2. Open the app — the split view renders the Threads sidebar plus a detail pane.
3. Open any thread.
4. Look for a way to start a new task, or to collapse the sidebar.
5. Compare with the same build on an Android phone (compose FAB present) or on an iPad (header has "New task" and sidebar-toggle buttons).

## Expected behavior

The Android tablet layout offers the same core actions as the phone layout and the iPad split view: a way to start a new task from anywhere (including while a thread is open) and a way to collapse/expand the sidebar. Filter/settings controls look and behave the same as their phone counterparts.

## Actual behavior

_[Personal note — describe what you ran into in your own words: which buttons you expected where, what you tried, and how it disrupted your workflow. 2–5 sentences.]_

---

**AI analysis (below this line).** The following code-level summary was produced by an AI coding agent that traced the behavior in the source; I reviewed it before filing. Line numbers refer to `main` @ `cec1bb9de`.

The app picks a layout purely from window size (`apps/mobile/src/lib/layout.ts:57` — ≥720dp wide and ≥600dp tall → split view), and the two modes are entirely separate component trees that fork at `HomeRouteScreen.tsx:100`:

- **Phone:** `HomeHeader` ("T3 Code" lockup, filter button, settings gear, search field) plus the compose FAB (`AndroidHomeFab.tsx`), and the thread list.
- **Tablet:** `ThreadNavigationSidebar` with its own separately-implemented header ("Threads" title, `sidebar-filter-button.tsx`, `sidebar-header-actions.tsx`), plus a detail pane.

On top of that split, several tablet controls are additionally gated to iOS, which is why the Android tablet is missing buttons rather than just rearranging them:

1. **No way to start a new task once a thread is open.** The compose FAB only exists in phone mode, and the split-view header's "New task" and sidebar-toggle buttons are iOS-native-header items only (`ThreadRouteScreen.tsx:622-662`); the Android header action list (`ThreadRouteScreen.tsx:663-710`) has files/terminal/git/inspector but neither of those two. On an Android tablet, new-task entry exists only on the empty "Select a thread" pane (`WorkspaceEmptyDetail.tsx:18`) and per-project rows.
2. **No sidebar collapse on Android tablets at all** — `WorkspaceSidebarToolbar` returns `null` on Android (`workspace-sidebar-toolbar.tsx:15`).
3. **Filter/settings buttons are parallel implementations** in `HomeHeader.tsx` versus the sidebar components, so their icons, grouping, and behavior have drifted apart.

**Possible direction (suggestion, not a demand):** three targeted changes rather than one big refactor — (a) add "New task" (and a sidebar toggle) to the Android split-mode header actions, and/or mount the compose FAB in the split branch too; (b) drop the Android early-return in `WorkspaceSidebarToolbar` or add an Android equivalent; (c) extract the filter-menu and settings buttons into shared components consumed by both the phone home header and the tablet sidebar so they can't diverge again. One caveat: because the layout is window-size-driven, a tablet in split-screen multitasking (or a phone in landscape, via the 600dp height floor) will intentionally fall back to the phone UI — that's by design, but it means any parity fix should be tested across both trees.

Happy to send a small, focused PR for (a) and (b) if there's interest — per CONTRIBUTING I'm raising the issue first.

## Impact

Minor bug or occasional failure _(bump to "Major degradation" if the missing new-task entry blocks your daily tablet use)_

## Version or commit

main @ cec1bb9de

## Environment

_[Fill in: tablet model + Android version, phone model + Android version, app build (e.g. Nightly), keyboard app if relevant.]_

## Screenshots, recordings, or supporting files

_[Attach: phone home screen (FAB visible), tablet split view (no FAB / no new-task button with a thread open), tablet sidebar header vs phone header side-by-side.]_

## Workaround

Starting a new task on an Android tablet is still possible from the empty "Select a thread" detail pane (before opening a thread) or from a project row's new-thread action.
