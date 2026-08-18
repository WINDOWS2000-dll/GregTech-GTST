package gregtech.api.recipes.ui;

/**
 * Describes the direction a recipe map's progress bar should fill in.
 */
public enum ProgressBarMoveType {
    /** Fills the progress bar upwards, from the bottom */
    VERTICAL,
    /** Fills the progress bar left to right */
    HORIZONTAL,
    /** Progress bar starts full, and empties upwards from the bottom */
    VERTICAL_INVERTED,
    /** Fills the progress bar clockwise in a circle, starting from the bottom left */
    CIRCULAR,
    /** Fills the progress bar downwards, from the top */
    VERTICAL_DOWNWARDS,
    /** Fills the progress bar right to left */
    HORIZONTAL_BACKWARDS
}
