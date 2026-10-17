TASK: {{problem}}

WHAT THIS SESSION DID:
{% for line in turns %}{{line}}
{% endfor %}{% if memories %}
MEMORIES YOU WERE SHOWN (flag any that are wrong):
{% for m in memories %}{{m.id}} [{{m.kind}}] {{m.content}}
{% endfor %}{% endif %}
