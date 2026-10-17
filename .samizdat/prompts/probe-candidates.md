# Candidate framings the probe cell tries against a stuck branch's current
# tape, one per line. Blank lines and lines starting with # are ignored.
#
# These are HARNESS messages: each is appended as one user turn on a COPY of
# the branch's tape, the model answers once, and the copy is thrown away. The
# framing whose answer carries a usable tool call is the one the branch is
# then steered toward. Order is the tie-break, so the earlier line wins among
# equals — express a preference by moving a line up.
#
# Keep them short and mechanical. A framing that asks for prose gets prose,
# and prose is exactly what the branch is already stuck producing.
[harness] Emit exactly one tool call now, as a fenced ```tool-call block, and nothing else.
[harness] What is the smallest single command that would tell you whether your last attempt was wrong? Issue it as one tool call.
[harness] Stop planning. Read the file you are about to change, as one tool call.
[harness] Your approach is not working. Pick a different tool than the one you last used, and call it.
[harness] Run the test that covers what you just changed, as one tool call.
