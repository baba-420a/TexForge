---
name: TexForge
colors:
  background: "#0F1115"
  surface: "#171A21"
  surface-variant: "#1F232C"
  outline: "#2A2F3A"
  on-surface: "#EDEFF3"
  on-surface-variant: "#9AA3B2"
  primary: "#5B8CFF"
  on-primary: "#0B1220"
  success: "#3DD68C"
  warning: "#F5B84B"
  error: "#FF6B6B"
typography:
  headline:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: 600
    lineHeight: 32px
  title:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: 600
    lineHeight: 24px
  body:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: 400
    lineHeight: 24px
  label:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: 500
    lineHeight: 20px
  caption:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: 400
    lineHeight: 16px
rounded:
  sm: 8px
  md: 12px
  lg: 16px
  full: 999px
spacing:
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
    height: 52px
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
    height: 52px
  button-text:
    backgroundColor: "transparent"
    textColor: "{colors.primary}"
    typography: "{typography.label}"
    height: 48px
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.lg}"
    padding: 16px
  card-selected:
    backgroundColor: "{colors.surface-variant}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.lg}"
    padding: 16px
  chip:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    height: 36px
  chip-selected:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    height: 36px
  bottom-nav:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface-variant}"
    height: 64px
  bottom-sheet:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.lg}"
    padding: 16px
---

## Overview
TexForge is a calm, professional tool for turning photos into PBR textures. The interface should feel like a quiet workspace: dark neutral surfaces, generous spacing, and one accent color. The texture images are the visual focus, so the interface itself stays visually quiet. The app is used one-handed on a phone, so everything important sits in the lower half of the screen.

## Colors
A dark, neutral base with a single blue accent.
- **background / surface / surface-variant:** three stacked neutral levels. Use background for screens, surface for cards, sheets and the bottom bar, and surface-variant for selected states.
- **outline:** hairline 1px borders and dividers only.
- **on-surface** for main text; **on-surface-variant** for secondary text and inactive icons.
- **primary:** the only accent. Use it for the single main action per screen, selected chips, sliders and active tab. Never use it for decoration.
- **success / warning / error:** only for status messages, never for decoration.
All text must meet WCAG AA contrast (4.5:1).

## Typography
Inter throughout. Use only the five styles defined above. Headline for screen titles, title for section and card titles, body for content, label for buttons, chips and tabs, caption for hints and map names. Sentence case everywhere; no all-caps and no decorative fonts.

## Layout
- Portrait phones only; design at 412 x 915 dp.
- 4px base grid. Screen margins 16px. Space between sections 24px.
- Minimum touch target 48 x 48 dp.
- Primary actions are full-width buttons pinned at the bottom of the screen above the navigation bar.
- Navigation: a bottom bar with exactly three tabs: Create, History, Settings. Inside a flow, use a simple top bar with a back arrow and a title. No drawers, no hamburger menus, no floating action buttons.
- Advanced controls (sliders, options) live in a bottom sheet, never on the main screen.

## Elevation & Depth
Flat design. Depth comes from the three surface colors and 1px outlines, not shadows. The only shadow allowed is a soft scrim behind bottom sheets. No glows, gradients, glassmorphism, or blur effects.

## Shapes
Rounded corners: 12px for buttons and inputs, 16px for cards and sheets, fully rounded for chips and segmented controls. Texture thumbnails use 12px corners. The 3D sphere preview sits on a plain background.

## Components
- **Primary button:** filled accent, one per screen, full width, 52px tall, short verb labels ("Generate", "Export").
- **Secondary button:** surface fill with an outline, same size. Text button for low-importance actions such as "Cancel".
- **Cards:** surface background with 16px padding. A selected card gets the surface-variant fill and a 1px primary outline. Used for the generation mode choice.
- **Chips:** single-row, horizontally scrollable choices such as maps or quality. The selected chip is filled with the accent.
- **Segmented control:** two or three options for switching views (AI Depth | Fast, Sphere | Tiling).
- **Sliders:** thin track, accent fill, with the label on the left and the value on the right.
- **Bottom sheet:** drag handle, title, then a short list of controls.
- **Icons:** simple outlined icons, always paired with a text label in navigation and buttons. A single icon style throughout.
- **Progress:** a linear bar with a one-line status text and a text "Cancel" button.
- **Texture images:** always shown edge to edge inside their rounded card on a neutral background, with a small caption below.

## Do's and Don'ts
- Do show one primary action per screen.
- Do keep labels to one or two words and use plain language.
- Do put advanced settings behind a single "Adjust" bottom sheet.
- Do use empty states with one sentence and one button.
- Don't use more than one accent color or any gradients.
- Don't show more than two button styles on a screen.
- Don't use nested menus, tooltips, or icon-only buttons.
- Don't put more than three tabs in the bottom navigation.
- Don't crowd screens; if a screen feels full, move content into a sheet or another step.