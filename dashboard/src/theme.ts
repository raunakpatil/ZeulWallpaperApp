import { createTheme, type MantineColorsTuple } from '@mantine/core';

// Brand: deep indigo-violet (Jobie-style), used for primary actions, links, focus glow and the sidebar.
const brand: MantineColorsTuple = [
  '#f0ebff', '#ddd3fe', '#b9a5fb', '#9474f8', '#744bf5',
  '#6031f3', '#5524f3', '#4517d9', '#3c13c3', '#310dac',
];
// Accent glow from the tactile reference: soft electric blue + warm gold highlights.
const glow: MantineColorsTuple = [
  '#e6f4ff', '#cde4ff', '#9bc7fd', '#65a8fb', '#3a8dfa',
  '#1f7cfa', '#0b73fb', '#0062e0', '#0057c9', '#004ab2',
];
const gold: MantineColorsTuple = [
  '#fff8e1', '#ffefcc', '#ffdd9b', '#ffca64', '#ffba38',
  '#ffb01b', '#ffab09', '#e39500', '#ca8400', '#af7100',
];

export const theme = createTheme({
  primaryColor: 'brand',
  primaryShade: { light: 6, dark: 4 },
  colors: { brand, glow, gold },
  fontFamily: 'Inter, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',
  headings: { fontFamily: 'Inter, system-ui, sans-serif', fontWeight: '700' },
  // Radius steps from the token sheet: 4 / 8 / 12 / 18.
  radius: { xs: '4px', sm: '8px', md: '12px', lg: '18px', xl: '24px' },
  // Spacing scale: 4 / 8 / 16 / 24 / 32.
  spacing: { xs: '8px', sm: '12px', md: '16px', lg: '24px', xl: '32px' },
  defaultRadius: 'md',
  defaultGradient: { from: 'brand.6', to: 'glow.5', deg: 135 },
  components: {
    Card: { defaultProps: { radius: 'lg' } },
    Button: { defaultProps: { radius: 'xl' } },
    TextInput: { defaultProps: { radius: 'md' } },
    Select: { defaultProps: { radius: 'md' } },
    Tooltip: { defaultProps: { withArrow: true, openDelay: 150, transitionProps: { transition: 'pop', duration: 120 } } },
    Switch: { defaultProps: { withThumbIndicator: false } }, // plain iOS-style knob
    Modal: { defaultProps: { radius: 'lg', overlayProps: { backgroundOpacity: 0.45, blur: 4 }, transitionProps: { transition: 'pop', duration: 220 } } },
    Drawer: { defaultProps: { transitionProps: { duration: 260, timingFunction: 'cubic-bezier(.2,.8,.2,1)' } } },
    Menu: { defaultProps: { transitionProps: { transition: 'pop-top-right', duration: 160 } } },
    Popover: { defaultProps: { transitionProps: { transition: 'pop', duration: 160 } } },
    Progress: { defaultProps: { transitionDuration: 700 } },
    RingProgress: { defaultProps: { transitionDuration: 700 } },
    Notification: { defaultProps: { radius: 'lg' } },
  },
});
