# System diagrams

Drawn from the code as built, so they agree with the source and with each
other. When the code changes, change the diagram source here and re-render;
do not hand-edit the PNGs.

| File | What it shows | Traced to |
|---|---|---|
| `1_concept_map.png` | Problem context → research gap → FocusFlow pipeline, privacy boundary, non-diagnostic constraint | `GazeAnalyzer`, `AttentionTracker`, `CalculateAttentionScoreUseCase`, `GenerateRecommendationsUseCase`, `AssessmentViewModel` |
| `2_sequence.png` | Onboarding, camera setup (`CalibrationGate`), the assessment loop with its three clip outcomes, profile generation, PDF export | the classes named on each lifeline |
| `3_activity.png` | User-facing flow including the 15-frame stability gate, skip/requeue/retire, exhausted pool, post-clip ratings | `CalibrationGate`, `AssessmentViewModel`, `AssessmentNavGraph` |
| `4_erd.png` | Remote (Supabase) vs local (Room) vs compiled dataset vs runtime-derived profile | `UserProfile`, `AssessmentHistoryEntity`, `AdhdSelfReportEntity`, `StimulusDataset`, `traitClusterWeights` |

## Re-rendering

Sequence, activity and ERD are Mermaid:

```bash
npx -y @mermaid-js/mermaid-cli -i 2_sequence.mmd -o 2_sequence.png -b white -s 2
```

The concept map is drawn with Pillow because Mermaid lays it out far too tall
for a portrait page:

```bash
python concept_map_pil.py
```
