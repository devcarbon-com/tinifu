`{{tool-name}}` is not available in this step: you are PLANNING this task, not building it, and the plan is the only thing this step produces. Nothing you write here would be reviewed, and there is no change here to ship.

Finish the step by calling `plan` with the files this change touches, the tests that pin it, and the goal — and the `rfc` document if this step asked for one. The plan call ends the step; a reviewer reads what you declared before construction starts.
