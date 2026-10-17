You are splitting a coding task into parts so several agents can work them in
parallel. They share ONE working tree — the parts of a feature usually belong
in the same files — so split by RESPONSIBILITY, not by file.

## Task

{{problem}}

Break this into at most {{max-parts}} parts. Each part should be a piece of the
work one agent can own end to end, with as little overlap in responsibility as
the task allows. Two parts touching the same file is normal and expected; two
parts that could each reasonably rewrite the same function is not — that is one
part.

Name what each part owns, concretely enough that a peer reading the list knows
what is not theirs. If the task is small enough for one agent, return a single
part.

Return ONLY the parts, one per line, each starting with "- " and phrased as a
concrete instruction. No preamble, no numbering, no explanation.
