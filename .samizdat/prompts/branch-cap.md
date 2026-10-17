{% if none %}Your branch_theses call asked for {{asked}} sibling branch(es), but
the run is at the cap of {{cap}} branches. None were spawned; assume the
existing branches already cover similar ground.{% else %}You asked for {{asked}}
sibling branch(es); the cap of {{cap}} allowed {{allowed}}. The rest were
dropped.{% endif %}
