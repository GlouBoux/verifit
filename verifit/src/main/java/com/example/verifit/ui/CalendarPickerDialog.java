package com.example.verifit.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.TextView;

import com.example.verifit.DataStorage;
import com.example.verifit.R;
import com.example.verifit.model.WorkoutDay;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

// Custom month calendar shown from the toolbar's calendar icon, replacing the plain
// Android DatePickerDialog. Unlike the platform DatePicker, this can mark which days
// already have a logged workout - a small dot under the day number, the same idea as
// FitNotes' own calendar navigation. Picking a day hands back a "yyyy-MM-dd" string,
// the same date format used everywhere else in the app (MainActivity.dateSelected,
// WorkoutDay.getDate(), ...).
public class CalendarPickerDialog extends Dialog
{
    public interface OnDaySelectedListener
    {
        void onDaySelected(String dateKey);
    }

    private static final SimpleDateFormat KEY_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private final OnDaySelectedListener listener;
    private final String selectedDateKey; // the day to highlight, if any ("yyyy-MM-dd")

    // Dates ("yyyy-MM-dd") that already have a logged workout, used to draw the dot.
    // Built once from dataStorage.getWorkoutDays() - the list actually populated at
    // app startup (DataStorage.loadWorkoutData()). DataStorage.getDays() looked like
    // the obvious source but is a leftover of CSV/session import only (populated by
    // setsToEverything()) and stays empty on a normal app launch - it never has the
    // day the app is actually showing, hence the dot not showing up at all.
    private final Set<String> daysWithWorkouts;

    // Only the year/month matter here - the day-of-month field is meaningless for the
    // grid itself, it's just whatever Calendar.getInstance()/setTime() leaves it as.
    private final Calendar displayedMonth = Calendar.getInstance();

    private TextView monthLabel;
    private DayGridAdapter adapter;

    public CalendarPickerDialog(Context context, DataStorage dataStorage, String initialDateKey, OnDaySelectedListener listener)
    {
        super(context);
        this.listener = listener;
        this.selectedDateKey = initialDateKey;
        this.daysWithWorkouts = buildDaysWithWorkouts(dataStorage);

        if(initialDateKey != null)
        {
            try
            {
                displayedMonth.setTime(KEY_FORMAT.parse(initialDateKey));
            }
            catch (ParseException e)
            {
                // Unparsable initialDateKey (shouldn't happen) - fall back to today,
                // already the Calendar.getInstance() default above.
            }
        }
    }

    private static Set<String> buildDaysWithWorkouts(DataStorage dataStorage)
    {
        Set<String> result = new HashSet<>();
        for(WorkoutDay day : dataStorage.getWorkoutDays())
        {
            // A day with no sets (shouldn't normally be in this list, but just in
            // case) has its date reset to "0000-00-00" by WorkoutDay.UpdateData() -
            // skip it explicitly rather than rely on that placeholder never matching.
            if(day.getSets() != null && !day.getSets().isEmpty())
            {
                result.add(day.getDate());
            }
        }
        return result;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.calendar_navigation_dialog);

        monthLabel = findViewById(R.id.calendar_month_label);
        GridView grid = findViewById(R.id.calendar_day_grid);
        ImageButton prevButton = findViewById(R.id.calendar_prev_month);
        ImageButton nextButton = findViewById(R.id.calendar_next_month);

        adapter = new DayGridAdapter();
        grid.setAdapter(adapter);

        grid.setOnItemClickListener((parent, view, position, id) -> {
            String dateKey = adapter.dateKeyAt(position);
            if(dateKey != null)
            {
                dismiss();
                listener.onDaySelected(dateKey);
            }
        });

        prevButton.setOnClickListener(v -> {
            displayedMonth.add(Calendar.MONTH, -1);
            refresh();
        });

        nextButton.setOnClickListener(v -> {
            displayedMonth.add(Calendar.MONTH, 1);
            refresh();
        });

        refresh();
    }

    private void refresh()
    {
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        monthLabel.setText(monthFormat.format(displayedMonth.getTime()));
        adapter.notifyDataSetChanged();
    }

    // One cell per day, Sunday-first, always 42 cells (6 full weeks) so the grid never
    // reflows height between months - including the leading/trailing days of the
    // neighboring months as blank, disabled filler cells.
    private class DayGridAdapter extends BaseAdapter
    {
        private static final int CELL_COUNT = 42;

        @Override
        public int getCount()
        {
            return CELL_COUNT;
        }

        @Override
        public Object getItem(int position)
        {
            return null;
        }

        @Override
        public long getItemId(int position)
        {
            return position;
        }

        // Returns the Calendar day shown at this grid position, or null if that cell
        // falls outside the displayed month (a filler cell).
        private Calendar dayAt(int position)
        {
            Calendar firstOfMonth = (Calendar) displayedMonth.clone();
            firstOfMonth.set(Calendar.DAY_OF_MONTH, 1);

            // Calendar.DAY_OF_WEEK: Sunday = 1 ... Saturday = 7, matching the grid's
            // Sunday-first column order.
            int leadingBlanks = firstOfMonth.get(Calendar.DAY_OF_WEEK) - 1;
            int dayOffset = position - leadingBlanks;

            int daysInMonth = firstOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
            if(dayOffset < 0 || dayOffset >= daysInMonth)
            {
                return null;
            }

            Calendar day = (Calendar) firstOfMonth.clone();
            day.set(Calendar.DAY_OF_MONTH, dayOffset + 1);
            return day;
        }

        String dateKeyAt(int position)
        {
            Calendar day = dayAt(position);
            if(day == null)
            {
                return null;
            }
            return KEY_FORMAT.format(day.getTime());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            View view = convertView;
            if(view == null)
            {
                view = LayoutInflater.from(getContext()).inflate(R.layout.calendar_day_cell, parent, false);
            }

            TextView dayNumber = view.findViewById(R.id.calendar_day_number);
            View workoutDot = view.findViewById(R.id.calendar_day_dot);

            Calendar day = dayAt(position);

            if(day == null)
            {
                // Blank filler cell (previous/next month) - not clickable, nothing shown.
                dayNumber.setText("");
                dayNumber.setSelected(false);
                workoutDot.setVisibility(View.INVISIBLE);
                view.setEnabled(false);
                return view;
            }

            view.setEnabled(true);

            String dateKey = KEY_FORMAT.format(day.getTime());
            dayNumber.setText(String.valueOf(day.get(Calendar.DAY_OF_MONTH)));

            boolean isSelected = dateKey.equals(selectedDateKey);
            dayNumber.setSelected(isSelected); // drives the circle background + text color

            boolean hasWorkout = daysWithWorkouts.contains(dateKey);
            workoutDot.setVisibility(hasWorkout ? View.VISIBLE : View.INVISIBLE);

            return view;
        }
    }
}
