# Skill Targets

RS3-style skill targets for RuneLite. Right-click any skill in the skills tab:

- **Set level target**: enter a level up to 126 (virtual levels allowed)
- **Set XP target**: enter an XP amount such as `13034431`, `13,034,431`, `2.5m` or `500k`, up to 200M
- **Clear target**: appears once a target is set

Skills with a target show a small target icon in their top-right corner. Hovering the skill adds the target, the target XP (or the level it reaches) and the XP remaining to the game's own skill tooltip, with a progress bar measured from the XP you had when you set the target.

When you reach a target, you get a chat message and a notification, and the target is cleared (configurable).

Targets are saved per account (RuneScape profile).

## Settings

- **Show target icon**: the icon on skills with a target
- **Add target to tooltip**: the extra tooltip lines and progress bar
- **Tooltip bar color**: color of the progress bar
- **Notify on target reached**: notification when a target is reached
- **Clear target when reached**: remove the target once it's reached

## Development

```
./gradlew run
```
