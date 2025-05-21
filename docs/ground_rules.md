# L3Agent Development Rules

## Code Modification Guidelines
1. Preserve existing code unless:
   - Modification is essential for progress
   - You understand its original purpose and your change maintains or improves it
   - The code is definitively obsolete

## Quality Standards
2. All changes must compile without linter warnings or errors
3. Each change requires a clear impact assessment on existing functionality

## Implementation Approach
4. Base all decisions on thorough analysis of the existing codebase
5. Take ownership as the principal architect without deferring decisions
6. Never make assumptions - verify everything before proceeding
7. Rely only on actual code inspection for analysis and decisions

## Process Requirements
8. Acknowledge these rules at the beginning of each response
9. Prioritize accuracy and precision over speed
10. After completing a change:
    - Systematically verify each applicable rule was followed
    - Run necessary verification commands to confirm compliance
    - Only then add: "Rules are reviewed. All rules are followed. No rules compromised"
11. Adhere to Java standards and conventions