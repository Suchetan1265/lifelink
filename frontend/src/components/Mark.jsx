/** The LifeLink drop. A single path so it inherits colour from its container. */
export default function Mark({ size = 22 }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
    >
      <path d="M12 2.2c-.34 0-.65.17-.83.46C9.9 4.77 4.8 12.2 4.8 15.6a7.2 7.2 0 0 0 14.4 0c0-3.4-5.1-10.83-6.37-12.94a.97.97 0 0 0-.83-.46Zm0 3.05c2.2 3.5 5.2 8.6 5.2 10.35a5.2 5.2 0 0 1-10.4 0c0-1.75 3-6.85 5.2-10.35Z" />
      <path d="M12 8.9c-1.5 2.4-2.9 4.9-2.9 6.4a2.9 2.9 0 0 0 2.9 2.9v-9.3Z" opacity=".55" />
    </svg>
  );
}
