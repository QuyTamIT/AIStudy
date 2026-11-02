---
name: AIStudy
description: Deep Sage learning workspace with open forms and editorial answer reading.
---

# AIStudy Design System

Approved direction: **Deep Sage + Premium Modern EdTech**, continuing Answer's editorial layout. Native Android Views/XML, AppCompat, and the existing Material Components Material 3 theme remain the UI stack.

## Overview

AIStudy helps learners ask a question, understand its saved Gemini answer, and explore related topics when available. The interface feels like a carefully typeset learning workspace: confident headings, open forms, a continuous reading surface, considered pauses, and restrained Sage emphasis. Home, Ask, Login, and Register adopt the established Answer system.

Learners read on phones in daylight and at night, often switching between a question, a formula, and an explanation. Light and dark appearances must both support sustained reading. The content supplies the personality; decorative AI symbols, gradients, oversized badges, and repeated card shells add no value here.

Answer's signature remains the contrast between a strong question, a continuous numbered learning path, isolated formulas, and a Sage result with a clear conclusion label. Ask puts the question composer first; Login and Register use open forms with a shared open-book brand mark. Use whitespace and type before borders or elevation. The subject, sequence, and conclusion each have a distinct visual role; the page must read as a learning interface even before its copy is read.

### Rollout and preservation contract

- **The current adoption changes Home only, as explicitly authorized on 2026-10-23.** This supersedes the earlier Home exclusion for this turn. MainActivity's shell, Bottom Navigation, Login, Register, Onboarding, Ask, Gemini, Answer, History, and Progress remain unchanged; do not continue into another screen or phase.
- Answer retains `Theme.AIStudy.Answer` and its `answer_*` resources. `Theme.AIStudy.Study` in `res/values/study_ui.xml` inherits that theme and adds the input and action styles for the adopted screens. LoginActivity and RegisterActivity apply it before `super.onCreate`; AskFragment and HomeFragment use locally themed inflaters. The application theme and Main shell remain unchanged.
- The adopted `fragment_ask.xml`, `auth_activity_login.xml`, and `auth_activity_register.xml` consume shared `include_study_*` layouts and additive `study_*` resources. Existing auth resources used by other screens are preserved.
- Home uses `fragment_home.xml`, `include_home_journey.xml`, `include_home_continue.xml`, and additive `home_*` resources. Read-only `UserDao.getById` and `QuestionAnswerDao.countByUserId` queries support its display; no entity, schema, migration, or database write changes are part of this adoption.
- Keep Gemini API calls, prompts, schemas, answer parsing, Room data, authentication, XP, navigation destinations, and business rules intact.
- Authentication remains in LoginActivity and RegisterActivity; Ask submission remains in AskFragment. `AuthFormUi` handles field errors, display state, and the password IME action only; it does not own authentication or persistence.
- Preserve answer text, order, formulas, code, final-answer conditions, and Show more topics eligibility, request count, limit, loading, retry, and error behavior.
- Formatting is a display concern. Never rewrite, summarize, normalize, or silently drop generated text to make a layout fit.
- Add no UI framework, remote font dependency, WebView, Markdown engine, or LaTeX library for this rollout.

### Token architecture

Use three layers: **primitive value → semantic role → component style**. Raw color values belong in primitive resources; layouts and drawables consume semantic roles. Component styles combine roles with dimensions and typography.

| Layer | Android representation | Example relationship |
|---|---|---|
| Primitive | `answer_colors.xml` and `answer_dimens.xml` in `res/values` | `answer_sage_700` = `#3F5F45`; `answer_space_4` = `16dp` |
| Semantic | `answer_*` color aliases, theme attributes, semantic dimensions | `answer_primary` → `answer_sage_700`; `answer_page_gutter` → width-qualified margin |
| Component | `answer_styles.xml`, `study_ui.xml`, state lists, `bg_answer_*` drawables | `Widget.AIStudy.Answer.More` and `Widget.AIStudy.Study.Primary` → primary/on-primary; expression/input → surface + outline |

Override semantic roles in `values-night`, rather than copying a second layout. Use dimension qualifiers for gutters. The `study_*` and `home_*` styles, selectors, and icons reuse `answer_*` semantic colors, spacing, and shapes instead of creating a competing palette.

## Colors

| Semantic role | Light | Dark | Purpose |
|---|---|---|---|
| Background | `#F7F8F5` | `#121A16` | Page and system-bar context |
| Surface | `#FFFFFF` | `#1B2520` | Contained code/formula and controls |
| Primary | `#3F5F45` | `#A9CBB0` | Main action, active emphasis |
| On primary | `#FFFFFF` | `#183222` | Text and icons on primary |
| Foreground | `#1E2A24` | `#E2EBE4` | Question, body, formulas, code |
| Secondary foreground | `#5E6B63` | `#ACB9B0` | Metadata and supporting status |
| Selected/result surface | `#E7EFE8` | `#293C2F` | Final result and selection emphasis |
| Outline | `#7C8A80` | `#7D9183` | Controls requiring a visible boundary |

Use the foreground role on the result surface. Separate low-emphasis section rules from essential control boundaries; a divider should not compete with type. Error uses a dedicated contrasting Material error role and a message, never color alone.

**The Semantic Palette Rule.** Adopted screens consume the existing semantic light/night roles; screen-specific styles do not establish another palette.

Text must reach WCAG AA contrast: 4.5:1 for ordinary text, 3:1 for large text and essential non-text controls. Disabled controls remain identifiable and cannot be mistaken for available actions. Test the actual foreground/background pair in both appearances.

## Typography

Use Android's system sans-serif family for UI and prose, and system `monospace` for code. This keeps Vietnamese coverage, font scaling, and native fallback reliable without network access.

| Role | Size | Line rhythm | Weight and usage |
|---|---|---|---|
| Ask/auth headline and Home hero | 32sp | 40sp | Semibold (600); page heading or question prompt |
| Home metric | 36sp | 44sp | Semibold (600), tabular numerals; saved question count and XP |
| Question/display | 28sp | About 36sp | Medium/semibold; complete question, left aligned |
| Section | 20sp | About 28sp | Medium/semibold; toolbar, Explanation, Explore further |
| Step title | 18sp | About 26sp | Medium/semibold; descriptive heading |
| Body | 16sp | About 26sp | Regular; sustained reading |
| Formula | 16sp | About 26sp | Regular monospace; literal expression |
| Code | 14sp | About 22sp | Regular monospace; preserve indentation |
| Final result | 22sp | About 32sp | Medium; conclusion within the result region |
| Label/status/action | 14sp | About 20sp | Regular or medium according to role |

Use `sp` for all text and scalable line height/spacing. Text containers grow with content. Never auto-shrink text, impose a fixed text height, or truncate a question, step, code block, or result. Do not bold entire body paragraphs. Use uppercase only for short metadata where language permits it; generated text retains its casing.

## Layout

- Spacing primitives: **4, 8, 12, 16, 24, 32, 48dp**.
- Gutter: **16dp** base, **24dp** at available width ≥360dp (`values-w360dp`), **32dp** at width ≥600dp (`values-w600dp`); keep related elements aligned.
- Centered columns: Answer and Home **720dp maximum**, Ask **640dp maximum**, Login and Register **480dp maximum**, with safe margins. Narrow screens use available width.
- Paragraph-to-title gap: 8dp; body-to-expression gap: 12dp; section/step grouping: 24–32dp; major closing separation: 32dp.
- Icons: one Material outline language in native vector drawables, normally **24dp**. Pair decorative icons with existing text only when they improve recognition.
- Interactive hit areas are at least **48 × 48dp**; action height may expand with font size. A 56dp visual target is preferred for primary actions where space allows.

### Responsive and accessibility rules

- Measure in `dp`; type in `sp`. Let the column contract, headings wrap, and controls grow before using alternative layouts.
- Account for system bars, display cutouts, and IME insets without applying the same inset twice. Keyboard appearance must not hide focus or prevent scrolling.
- Answer has no new text-entry flow. Native text selection, hardware-keyboard navigation, and any existing interactions remain usable.
- Ask and auth use vertically scrollable, growing layouts. Auth applies system-bar, cutout, and IME insets at its content root; Ask adds only remaining IME overlap after the Main shell's system bars and Bottom Navigation. This is the implemented inset strategy, not a claim that every full-height keyboard case has been exercised.
- At large font sizes, use the full single-column width; keep headings, action labels, errors, and statuses unclipped. Do not compress type to protect a preferred composition.
- Home's two metrics stack vertically at font scale ≥1.3 or available width <360dp, with the inter-column divider removed. The greeting, saved question, metadata, and action labels wrap without truncation.
- Horizontal scrolling is confined to genuine code/formula overflow. It must not be required to read ordinary prose or press an action.
- Preserve contrast, focus, TalkBack order, touch targets, and selection in light/dark appearances. Do not encode progress, error, or selection solely through color.

## Elevation & Depth

**The Flat Surface Rule.** Use flat surfaces with minimal elevation. Reserve tonal surface contrast for code/formulas, results, or controls that need containment. The adopted forms and suggestion rows remain on the page surface; primary actions have no elevation animator. Native ripple and focus treatments communicate interaction.

## Shapes

Use **8dp** corners for formula/code regions, step markers, and text-link or suggestion feedback; use **12dp** corners for the final-result region, inputs, and primary action surfaces. Avoid pills for long text and nested cards. Quiet section rules provide grouping; control outlines make interaction boundaries visible.

## Components

### Answer composition

Keep the back action in a stable top region. Put the learning content below it in a single vertically scrollable reading column; the page itself never scrolls sideways.

1. Compact native toolbar: a 48dp outlined Back control and the screen title, in the same centered column as the content.
2. Subject metadata in a compact Sage label with an outline book icon.
3. Complete question as the strongest typographic element, followed by a full-width quiet section rule.
4. Explanation heading with the actual number of steps, aligned opposite it. At font scale ≥1.3, place the count below the heading so it keeps the full reading width.
5. Ordered steps along a continuous vertical path: separate two-digit number, descriptive heading, body, optional expression or code region.
6. Final answer in a Sage-tinted closing region **only when the existing model permits it**.
7. Explore further, followed by More topics status/errors and the existing action **only under the existing eligibility rules**.

Do not place each step in a card. Section spacing creates continuity; only specialized content receives a containing surface. Preserve existing labels and answer ordering.

#### Learning path

- Use a **32dp minimum** number marker with an 8dp radius, native medium numerals, surface fill, and a quiet 1dp outline. Let it grow with system text size.
- Separate the marker from the text column by **12dp**. Title, prose, formulas, and code share that text alignment.
- A **1dp vertical connector** runs between markers, with 8dp breathing room at each end. It represents sequence rather than completion; do not add progress ticks or imply mastery.
- Use **32dp** between step groups. The final step has no outgoing connector. Appending topics reconnects the prior last step without replacing the existing views or moving the reader.
- The marker is decorative for TalkBack; the step heading retains the complete numbered accessible name. Empty explanations have no numbered marker or connector.

#### Prose and long answers

- Left-align text using start/end layout directions. Allow natural wrapping; preserve the original paragraph breaks and characters.
- Mark section/step titles as accessibility headings. Keep reading order identical to visual order.
- Keep learner-selectable question, prose, expressions, and results selectable. Avoid a parent click handler that captures text selection.
- Loading more must retain existing step views and their text; append the returned steps without rebuilding the already-read answer.
- Preserve the reader's position when data/status updates. Do not auto-jump to newly appended content.

#### Formulas

- Display the existing plain-text `expr` as literal text. Do not translate notation, replace operators, or fabricate mathematical layout.
- Use a dedicated expression surface with **16dp** padding and an optional quiet boundary.
- Permit horizontal scrolling within a formula region when it cannot fit. Keep its scroll independent from vertical page reading.
- Empty expressions have no visible surface or reserved blank space.
- This rollout does not render LaTeX, stacked fractions, matrices, or mathematical markup; those require a separate data/rendering decision.

#### Code and mixed content

- Explicit fenced code can receive a native monospace display region; retain **all original characters**, whitespace, line endings, and fence text so selection does not silently change the answer.
- Code keeps indentation and does not wrap merely to fit. Permit horizontal scrolling inside the code region while the rest of the answer remains within the column.
- Body content without an unambiguous code boundary stays as received. Never infer code from punctuation, language keywords, or the selected subject.
- Do not apply regex cleanup, syntax substitution, guessed highlighting, or Markdown transformation to generated text.
- If a block is malformed or ambiguous, readable plain text is the fallback. No generated content may disappear because classification failed.

#### Final answer and Show more topics

The result region is a visual conclusion, not a second answer source. Its contents and visibility come from the current model. Use 24dp padding, a compact filled Sage label with a check icon, and 22sp medium result text. The check identifies the conclusion; it does not claim independent verification. If the result is hidden, remove its surrounding container and spacing as well.

Show more topics remains the existing action. Keep all current states: unknown answer type/checking, eligible, requesting, count, limit reached, and recoverable error. Disabled/loading states must be obvious, labels may wrap, and errors stay close to the action with a polite live announcement. Existing content stays readable during a request.

Group this action under **Explore further**, with the spinner beside the status rather than on a separate row. Keep the existing request count and all button labels. The filled button has a trailing plus while available; remove that affordance during loading or at the limit. Use a flat surface with no button elevation, native press ripple, a tonal outlined keyboard-focus state, and explicit disabled colors. Do not add another surrounding card.

#### Navigation and interaction

- Preserve the existing Back behavior and destination.
- Use native button feedback and visible keyboard focus. Icon-only navigation requires an accessible name.
- Loading indicators announce purpose where appropriate; decorative indicators are excluded from duplicate announcements.
- Motion clarifies changes: **120ms** feedback and **180ms** state transitions when needed. Respect system animation settings; no reading-surface entrance animation, shimmer, or automatic movement.

### Component adoption beyond Answer

The following patterns are implemented in Ask, Login, Register, and Home. Only Home is authorized for the current adoption.

#### Shared identity and controls

- The shared brand row uses the existing outline open-book vector inside a 48dp Sage mark, with the AIStudy name and existing subtitle. Its icon is decorative for accessibility; the adjacent text carries the identity.
- Primary actions inherit Answer's flat Sage button, 12dp radius, native press ripple, outlined focus state, explicit disabled colors, and flexible height with a 56dp minimum. Study actions use 16sp text, 24dp horizontal padding, and a trailing arrow while available. Text links use a 48dp minimum target and an 8dp radius; focus/hover receives a tonal surface.
- Material outlined fields have a surface fill, 12dp radius, 1dp resting outline, and a 2dp focused Sage outline. Error outlines and inline messages use the shared error role. Disabled outlines use the divider role. Native floating-label and ripple feedback supply the micro-interactions; no decorative entrance or looping animation is added.
- General errors use the existing error surface with a native vector error icon and a polite live region. Native loading rows place a 20dp indeterminate indicator beside status text; the indicator itself is excluded from duplicate accessibility announcements.
- Keep forms directly on the page background. Contain the composer and individual controls rather than enclosing the whole form or every suggestion in another card.

#### Ask

The reading order is brand, headline and support, question label and composer, error/action/loading, then suggestions. The multiline composer has a 160dp minimum height and four minimum lines, growing with content; the persistent visible label stays outside the field. An empty submission sets an inline field error and focuses the composer. Request or service errors use the nearby general error banner. Editing clears displayed errors.

Suggestions are flat, full-width rows separated by quiet rules, with a subject-specific outline vector on the leading edge and a decorative trailing arrow. Each row has a 64dp minimum height and grows with text. Selecting a suggestion fills the existing composer, places the cursor at the end, focuses it, and requests the native keyboard so the learner can edit before submitting.

While the existing request is active, the composer, Send action, and suggestion rows are disabled; the button uses its existing sending label and removes the arrow, and a native loading/status row appears. These are display bindings around the unchanged Gemini worker, save transaction, XP update, and pending-answer navigation.

#### Login and Register

Use the same single-column open form: brand, headline/support, labeled fields, error/action/loading, quiet divider, and the existing mode-switch link. Login keeps email then password; Register adds name first. Native persistent floating labels identify fields after entry. Email and name use the appropriate input types and autofill hints; password uses native visibility control, password autofill for Login, and `newPassword` autofill for Register. Register retains the existing password helper text.

The keyboard moves through name/email with Next; Done on password invokes the existing enabled submit action. Field-specific validation appears inline and focuses the first affected field. General failures appear in the error banner. Editing clears the edited field's message and the general banner. Validation rules, authentication methods, successful registration toast, and destinations remain unchanged.

During submission, fields, the primary action, and mode switch are disabled. The existing loading button label remains; its arrow is removed and the native loading/status row is shown. Form interaction returns when the existing submission state clears.

#### Home dashboard — adopted 2026-10-23

Use a text-only **AIStudy** wordmark beside the existing Logout action; do not add an agent logo. Follow it with a time-of-day greeting and the saved user's full name from read-only `UserDao.getById`, falling back to the greeting alone. Show saved Profile level/style and interests only when recognized values exist, reusing the existing onboarding labels.

The question prompt is a solid Sage hero with 12dp corners, 24dp padding, and on-primary type. Its contrasting **Ask a question** action reuses the native Study primary control with inverted semantic colors and selects the existing `nav_ask` item. Learning Journey remains open on the page: actual per-user `COUNT(*)` of saved questions and existing `Profile.xp`, with localized integer formatting. Missing Profile XP displays **Unavailable**. Do not invent a target, completion percentage, quiz, leaderboard, achievement, or streak.

Continue Learning shows the actual latest saved question through existing `getLatestByUserId`, with its subject, available saved date, and **Revisit explanation** action. The whole Sage-tinted row opens `AnswerActivity.EXTRA_QA_ID` for that record and exposes a button role and full question to accessibility. Preserve the complete question. With no saved answer, remove the latest-question subtitle and row, and show the true empty state with **Ask your first question**, also selecting `nav_ask`.

Load a consistent, read-only Room snapshot on `Dispatchers.IO`, using the per-user count query rather than materializing answer history. Refresh on `onResume`; cancel previous loads and use the view lifecycle so detached views do not receive results. Loading and recoverable error/retry states are explicit and use polite live regions; a read failure must not appear as zero progress. Keep the Ask action available. Verify that the active user still matches before binding data or opening a saved answer. Native ripple and visible focus provide feedback; add no decorative entrance motion.

#### Remaining adoption boundary

Main shell, Bottom Navigation, Onboarding, History, and Progress retain their existing implementation. Ask, Login, Register, and Answer retain their previously adopted implementation during the Home work. Future adoption may reuse these evidenced tokens and controls only within a separately authorized scope.

## Do's and Don'ts

- **Do** reuse the shared semantic palette, type, and gutters; preserve the open-book identity in Ask/auth and the text wordmark in Home.
- **Do** let text, fields, errors, and actions grow with content and system font size.
- **Do** keep generated content and all established business behavior intact while changing its presentation.
- **Don't** add decorative card shells, AI symbols, or motion that competes with the learning task.
- **Don't** extend the current Home adoption into the Main shell, Bottom Navigation, Login, Register, Onboarding, Ask, Gemini, Answer, History, or Progress.
- **Don't** treat a source-level state binding as runtime or accessibility verification.

### Verification and handoff

Use **`assembleDebug`** for build verification. Do not run Instrumented Tests, commit, or push automatically. The coverage and limits below distinguish the Home adoption from earlier Ask/auth and Answer work.

#### Home adoption — 2026-10-23

The first `assembleDebug` passed in 1 minute 11 seconds. The final build after review fixes passed in 1 minute 21 seconds (37 actionable tasks). No JVM unit tests or Instrumented Tests were run; no commit or push was performed. The changed existing main sources for this task are HomeFragment, `fragment_home.xml`, UserDao, and the read-only `countByUserId` addition to QuestionAnswerDao, alongside additive Home resources. Other screen implementations and existing business behavior remain at their saved source baseline.

Runtime round-one evidence is in ignored `build/home-ui-review/round1`, with `evidence.json` in the parent directory. Inspection covered light/dark appearances, compact 200% font size, tablet width, existing Ask tab navigation, the latest saved Answer and Back, and the empty-account Ask action. The emulator used actual persisted account data; no fixture was inserted. Before/after hashes for users, profiles, and question_answers match, and authentication/display settings were restored.

The independent finish review passed after confirming both findings resolved: the empty-state subtitle and full-history materialization. The detector ran once and returned `[]`. Round-two populated evidence in `build/home-ui-review/round2` confirms the existing account's 10 questions, 500 XP, and latest answer. Its empty-state capture is black and invalid; round one remains the valid empty-layout image, while source/XML confirms the final subtitle is hidden without a saved question. The compact round-two capture shows XP and the latest answer after scrolling, so it does not establish complete large-font metric visibility. Both runtime scripts completed and restored authentication/display settings.

TalkBack speech, runtime keyboard focus, landscape, runtime loading/error/retry, complete final large-font metrics, the final empty-state visual appearance, and timing with large answer histories remain unverified. Source bindings establish those states and focus treatments, not their runtime verification.

#### Ask, Login, and Register adoption — 2026-10-21

`assembleDebug` passed after each adopted screen; the latest combined build passed in 54 seconds. No JVM unit tests or Instrumented Tests were run for this adoption. `build/sage-ui-before/verify_scope.py` confirms that the `login()` and `register()` method bodies, Gemini worker, Room transaction, XP update, prompts, parsing, error mapping, and pending-answer navigation match their saved baseline. All other existing main sources match that baseline; the only changed existing main files are these three screens' layouts and Activities/Fragment. New shared resources and the display-only `AuthFormUi` provide the adopted components.

Visual evidence is in the ignored `build/sage-ui-review/round1` directory: `ask-light`, `ask-empty-error`, `ask-keyboard`, `ask-dark`, `ask-tablet`, `login-light`, `login-error-keyboard`, `login-dark`, `register-light`, `register-empty-error`, and `register-compact-font200-keyboard` PNGs. Review covered light/dark styling, inline empty-field errors, tablet-width Ask, and compact Register at 200% font size with a focused password and a full-height QWERTY keyboard. The default Ask/Login IME images show the OS floating handwriting toolbar, so they do not establish full-height keyboard behavior.

Still unverified: compact 200% inline error/helper wrapping, loading appearance at runtime, full-height IME scrolling to the action across all three screens, and TalkBack speech. No live Gemini request or successful authentication submission was made for QA. No database fixture or preview Activity was added. The temporary preview session and emulator settings were backed up and restored. No commit or push was performed.

#### Earlier Answer refinement — retained history

Answer refinement validation on 2026-10-21: **`assembleDebug` and all 21 JVM unit tests passed**. API, repository, ViewModel, prompts, parsers, Ask, and database source hashes match the pre-refinement baseline. Kotlin edits are confined to view binding and accessibility layout. No Instrumented Tests, commit, or push were performed.

Build environment: the existing daemon criteria auto-detected a VS Code JRE without `jlink`. Validation selected the already-installed full JDK 25 through command-line overrides; no project or user Gradle configuration was changed. Reproduce from the project directory in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
.\gradlew.bat '-Porg.gradle.java.installations.paths=C:\Program Files\Java\jdk-25' '-Porg.gradle.java.installations.auto-detect=false' --no-daemon assembleDebug testDebugUnitTest --console=plain
```

Emulator inspection covered a saved math result, a three-step topic answer and its available More topics action, light/dark appearances, a 320dp viewport with 200% system font size, and a tablet-width programming answer. The large-font inspection found an orphaned heading letter; the section count now moves below the heading at accessibility font sizes. Emulator appearance, font scale, resolution, and density were restored after review. Preview helpers and screenshots live only in the ignored `build/answer-ui-before` directory; no preview Activity or test fixture was added to app sources.

Loading, retry, error, limit, and append rules were checked against their existing bindings and unit tests; no live More topics request was made for visual QA. TalkBack speech, text-selection gestures, long-formula horizontal scrolling, landscape, and IME behavior were not exercised in that earlier Answer review. This history records Answer's prior verification; it does not claim those checks were rerun for the Ask/auth adoption. Answer remains unchanged by the current adoption.
