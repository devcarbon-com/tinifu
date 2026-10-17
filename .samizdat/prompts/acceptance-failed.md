This run has acceptance criteria it has not met. They are the operator's definition of done, checked over the tree as it stands, and the answer cannot satisfy them by describing the work — the tree has to.

{% for r in failed %}- FAIL **{{r.name}}**{% if r.output %}
```
{{r.output}}
```{% endif %}
{% endfor %}{% if passed %}Met: {{passed|join:", "}}.

{% endif %}Fix what each failing criterion names, run it yourself to see it pass, then call `done` again.
