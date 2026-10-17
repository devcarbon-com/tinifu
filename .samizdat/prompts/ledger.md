## What this run has settled, and what it has only planned

{% if established %}### Established — engine-verified in this run
{{established}}

{% endif %}{% if ruled-out %}### Ruled out — engine-REFUTED, do not re-attempt these
{{ruled-out}}

{% endif %}{% if sketches %}### Sketches — UNVERIFIED PLANS, not results; every step is still open
{{sketches}}

{% endif %}{% if inherited %}### Inherited — confirmed by the run this one was seeded from
{{inherited}}

{% endif %}Fetch any encoding with `fetch_artifact` and its id, e.g. `a#12`, `s#7` or `p#3`.
