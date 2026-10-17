[harness] {% if deadline %}Your previous turn exceeded the {{seconds}}s deadline and was cancelled{% else %}The harness restarted{% endif %}{% if committed %} after `{{tool}}` had completed. Its result is above; nothing after it happened, so pick up from there.{% else %} while `{{tool}}` was running.{% endif %}
{% if unknown %}
{{uncertain}}
{% endif %}{% if no-effect %}
`{{tool}}` only reads, so nothing changed. Make the call again if you still need what it would have returned.
{% endif %}
Keep your next response short and call a tool.
