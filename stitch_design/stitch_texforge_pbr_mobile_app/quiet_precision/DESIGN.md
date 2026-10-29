---
name: Quiet Precision
colors:
  surface: '#171A21'
  surface-dim: '#111317'
  surface-bright: '#37393e'
  surface-container-lowest: '#0c0e12'
  surface-container-low: '#1a1c20'
  surface-container: '#1e2024'
  surface-container-high: '#282a2e'
  surface-container-highest: '#333539'
  on-surface: '#EDEFF3'
  on-surface-variant: '#9AA3B2'
  inverse-surface: '#e2e2e8'
  inverse-on-surface: '#2f3035'
  outline: '#2A2F3A'
  outline-variant: '#434653'
  surface-tint: '#b2c5ff'
  primary: '#b2c5ff'
  on-primary: '#0B1220'
  primary-container: '#5b8cff'
  on-primary-container: '#002665'
  inverse-primary: '#1857c8'
  secondary: '#b5c5f7'
  on-secondary: '#1e2f58'
  secondary-container: '#354570'
  on-secondary-container: '#a4b4e5'
  tertiary: '#ffb874'
  on-tertiary: '#4b2800'
  tertiary-container: '#d47b00'
  on-tertiary-container: '#412200'
  error: '#FF6B6B'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#dae2ff'
  primary-fixed-dim: '#b2c5ff'
  on-primary-fixed: '#001847'
  on-primary-fixed-variant: '#0040a0'
  secondary-fixed: '#dae2ff'
  secondary-fixed-dim: '#b5c5f7'
  on-secondary-fixed: '#051942'
  on-secondary-fixed-variant: '#354570'
  tertiary-fixed: '#ffdcbf'
  tertiary-fixed-dim: '#ffb874'
  on-tertiary-fixed: '#2d1600'
  on-tertiary-fixed-variant: '#6a3b00'
  background: '#111317'
  on-background: '#e2e2e8'
  surface-variant: '#1F232C'
  success: '#3DD68C'
  warning: '#F5B84B'
typography:
  headline-lg:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.005em
  title-md:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: 0em
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0em
  label-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.02em
  caption-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.01em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style
The design system embodies a calm, precise, utilitarian dark aesthetic tailored for mobile 3D and technical workflows. Built for mobile artists and technical directors turning photographs into PBR material maps, the user interface acts as a silent, invisible workbench. Rather than competing with complex diffuse, normal, roughness, and displacement textures, the UI recedes completely into deep, low-luminance neutral planes.

The system fuses technical minimalism with Android ergonomics:
- **Utilitarian Serenity:** Dark, non-distracting chromatic fields ensure color and value fidelity for user texture assets.
- **Single-Focus Hierarchy:** Singular actions per screen, preventing cognitive overload in technical settings.
- **Tactile Directness:** One-handed ergonomics concentrated in the lower thumb zone, with complex parameter adjustments isolated within dedicated bottom sheets.
- **Restrained Chrome:** Flat surfaces, strict 1px hairline delimiters, zero extraneous decoration, and zero artificial dropshadows.

## Colors
The color architecture relies on a dark neutral continuum with a single focused blue chromatic accent. All visual weight is distributed strictly by luminance hierarchy rather than hue.

### Neutral Layering
- **Canvas Base (`#0F1115`):** The foundational backdrop across all screens.
- **Surface (`#171A21`):** The primary container level for persistent toolbars, resting cards, and bottom sheets.
- **Surface-Variant (`#1F232C`):** Used strictly for active selection, pressed states, or focused interactive segments.
- **Outline (`#2A2F3A`):** Standard 1px structural boundary defining component silhouettes without contrast disruption.

### Text & Accents
- **Primary Accent (`#5B8CFF`):** Reserved exclusively for active selection states, singular main action buttons, active slider tracks, and active tabs. It must never be applied decoratively or as subtle backgrounds.
- **On-Primary (`#0B1220`):** Ultra-dark contrast text providing optimal readability against the saturated primary accent.
- **On-Surface (`#EDEFF3`) & On-Surface-Variant (`#9AA3B2`):** Maintain strict WCAG AA contrast (≥ 4.5:1) for primary headers, titles, and subdued metadata.
- **Feedback Accents (`#3DD68C`, `#F5B84B`, `#FF6B6B`):** Strictly reserved for system feedback, status tags, and error indicators.

## Typography
Typographic styling uses **Inter** across all functional tiers to guarantee crisp pixel alignment on mobile screens.

- **Case Discipline:** All UI elements use sentence case. Never use all-caps, even for buttons, tabs, or badges.
- **Hierarchy Rules:** 
  - `headline-lg` is restricted to screen-level titles.
  - `title-md` designates section headers, card titles, and modal headers.
  - `body-md` conveys descriptive instructions and step explanations.
  - `label-md` governs interactive elements: buttons, segmented controls, bottom bar tabs, and chips.
  - `caption-sm` delivers texture metadata, parameter readouts, and map tags (e.g., Albedo, Roughness, Normal).

## Layout & Spacing
The layout follows a portrait-first, handheld Android viewport specification (designed natively at 412 × 915 dp) operating on a strict 4px / 8px incremental grid.

- **Canvas Margins & Gutters:** A consistent 16px (`margin`, `1rem`) safe boundary frames the viewport. Grid column gutters across multi-card comparisons are standardized to 16px (`gutter`, `1rem`).
- **Section Rhythm:** Inter-section spacing defaults to 24px (`space-lg`), separating control zones from viewport representations.
- **Ergonomic Allocation:** 
  - The upper 50% of the screen is dedicated to previewing generated PBR materials and 3D mesh viewports.
  - The lower 50% hosts interactive controls, modes, and generation triggers within immediate reach of one-handed thumb arcs.
  - Persistent bottom sheets host secondary adjustments, keeping primary viewport workflows uncluttered.

## Shapes
Corner geometry communicates affordance, grouping, and container boundaries using consistent scale mappings:

- **16px (`rounded-lg` / 1rem):** High-level structural containers, major content cards, preview panels, and bottom sheets.
- **12px (`rounded` / 0.75rem):** Form inputs, action buttons, mode tiles, and texture thumbnail tiles.
- **8px (`rounded-sm` / 0.5rem):** Embedded controls, small parameter badges, and compact tool icons.
- **Pill (`rounded-full` / 999px):** Filter chips, segmented control sleeves, slider thumbs, and bottom sheet drag handles.

## Components

### Buttons
- **Primary Button:** Fixed 52px height, full-width or pinned thumb-docked. Background `#5B8CFF`, label `#0B1220` in `label-md` weight 500. 12px corner radius. Exactly one allowed per screen.
- **Secondary Button:** Fixed 52px height. Background `#171A21`, 1px border `#2A2F3A`, label `#EDEFF3`.
- **Text Button:** Fixed 48px height minimum touch target. Transparent background with `#5B8CFF` or `#9AA3B2` text for dismissive actions.

### Cards & Selection Containers
- **Resting Card:** Surface `#171A21`, 1px border `#2A2F3A`, 16px padding, 16px radius.
- **Selected Card:** Surface `#1F232C`, 1px border `#5B8CFF`, 16px padding, 16px radius.
- **Texture Card:** Edge-to-edge asset display with a 12px radius inside parent surface cards, accompanied by `caption-sm` label rows below.

### Chips & Segmented Controls
- **Filter Chips:** 36px height, pill radius (999px). Unselected: Surface `#171A21`, border `#2A2F3A`, text `#9AA3B2`. Selected: Background `#5B8CFF`, border `#5B8CFF`, text `#0B1220`.
- **Segmented Control:** Enclosing track in `#171A21` with 1px `#2A2F3A` border. Active segment fills with `#1F232C` and text shifts from `#9AA3B2` to `#EDEFF3`.

### Inputs & Sliders
- **Numeric/Text Inputs:** 52px height, `#171A21` background, 1px `#2A2F3A` border, 12px radius. Active focus switches border to `#5B8CFF`.
- **Parameter Sliders:** 4px tall track in `#2A2F3A`, active fill in `#5B8CFF`. 20px pill thumb in `#5B8CFF`. Parameter title aligned left (`label-md`), real-time value readout aligned right (`caption-sm` in `#9AA3B2`).

### Bottom Sheets & Navigation
- **Bottom Navigation:** 64px height, background `#171A21`, top border 1px `#2A2F3A`. Exactly 3 tabs (Create, History, Settings). Active tab icon & label in `#5B8CFF`; inactive in `#9AA3B2`.
- **Bottom Sheet:** Background `#171A21`, top radius 16px, 1px `#2A2F3A` border. Centered 32×4px drag handle in `#2A2F3A` with 8px vertical padding. Hosts secondary technical parameters without leaving the viewport.