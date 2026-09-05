import { Component } from 'react';

/**
 * Without this, any render error unmounts the tree and leaves a blank white
 * page with the cause only in the console. Showing the error on the page makes
 * a failure diagnosable by whoever hits it.
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    console.error('LifeLink crashed while rendering', error, info);
  }

  render() {
    if (!this.state.error) return this.props.children;

    return (
      <div style={{ maxWidth: '38rem', margin: '12vh auto', padding: '0 1.5rem' }}>
        <h1>Something broke on this screen</h1>
        <p className="muted">
          The page failed to render. Reloading usually clears it; if it keeps happening the detail
          below is what to report.
        </p>
        <pre
          style={{
            background: 'var(--surface)',
            border: '1px solid var(--line)',
            borderRadius: '8px',
            padding: '1rem',
            overflowX: 'auto',
            fontSize: '0.8rem',
          }}
        >
          {String(this.state.error?.stack || this.state.error)}
        </pre>
        <button type="button" className="button" onClick={() => window.location.reload()}>
          Reload the page
        </button>
      </div>
    );
  }
}
