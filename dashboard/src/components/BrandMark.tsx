import zeulIcon from '../assets/zeul-icon.webp';

/**
 * App mark: the Zeul hamster (brand/zeul-hamster-master.jpg, cropped like the launcher icon by
 * brand/build_icons.py). Rounded like an app icon; retro.css squares it off with a bevel.
 */
export function BrandMark({ size = 34 }: { size?: number }) {
  return (
    <img className="brand-mark" src={zeulIcon} width={size} height={size} alt="" aria-hidden draggable={false}
      style={{ borderRadius: Math.round(size * 0.27) }} />
  );
}

/** Mark + wordmark used in the sidebar, mobile header and sign-in screen. */
export function BrandLockup({ size = 34, caption = 'Admin' }: { size?: number; caption?: string }) {
  return (
    <div className="brand-lockup">
      <BrandMark size={size} />
      <div style={{ lineHeight: 1.05, minWidth: 0 }}>
        <div className="ig-text brand-word">Zeul<span className="brand-word-light"> Studio</span></div>
        {caption && <div className="brand-caption">{caption}</div>}
      </div>
    </div>
  );
}
