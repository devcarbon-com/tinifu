You are not alone in this working tree. Other branches on this run have changed these files:
{% for f in files %}- `{{f.path}}` — {{f.branches}} (last changed on their turn {{f.turn}})
{% endfor %}
`write_file` replaces a whole file. Before you write one of these, read it — what
is in it now is not what you last saw, and writing from memory drops whatever a
sibling added. Prefer `edit_file` on a file someone else is in, and use the
message tool to say which part you are taking.
