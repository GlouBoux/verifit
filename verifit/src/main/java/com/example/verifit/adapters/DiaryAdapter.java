package com.example.verifit.adapters;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.R;
import com.example.verifit.model.WorkoutDay;
import com.example.verifit.ui.DayActivity;
import com.example.verifit.ui.MainActivity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

// Adapter for WorkoutDay Class
public class DiaryAdapter extends RecyclerView.Adapter<DiaryAdapter.MyViewHolder> {

    Context ct;
    ArrayList<WorkoutDay> Workout_Days;

    public DiaryAdapter(Context ct, ArrayList<WorkoutDay> Workout_Days)
    {
        this.ct = ct;
        this.Workout_Days = new ArrayList<WorkoutDay>(Workout_Days);
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(this.ct);
        View view = inflater.inflate(R.layout.diary_row,parent,false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position)
    {

        // Set view's data
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd");

        // Possible Error
        try {

            Date date = parser.parse(Workout_Days.get(position).getDate());
            SimpleDateFormat formatter1 = new SimpleDateFormat("EEEE");
            SimpleDateFormat formatter2 = new SimpleDateFormat("MMMM dd YYYY");

            // Change TextView texts
            holder.tv_day.setText(formatter1.format(date));
            holder.date.setText(formatter2.format(date));


            // Change RecyclerView items
            DiaryExerciseAdapter workoutExerciseAdapter = new DiaryExerciseAdapter(ct, MainActivity.dataStorage.getWorkoutDays().get(position).getExercises());
            holder.recyclerView.setAdapter(workoutExerciseAdapter);
            holder.recyclerView.setLayoutManager(new LinearLayoutManager(ct));


        }
        catch (ParseException e)
        {
            e.printStackTrace();
        }

        // Day Dialog
        holder.cardview_diary.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                navigateToDay(position);
            }

        });

        // More Button
        holder.expand_button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                System.out.println("More Button");
            }
        });
    }

    public void navigateToDay(int position)
    {
        Intent in = new Intent(ct, DayActivity.class);
        // Update Date Selected in MainActivity
        MainActivity.dateSelected = Workout_Days.get(position).getDate();
        in.putExtra("date",Workout_Days.get(position).getDate());
        ct.startActivity(in);
    }

    @Override
    public int getItemCount()
    {
        return Workout_Days.size();
    }

    public class MyViewHolder extends  RecyclerView.ViewHolder
    {
        TextView tv_day;
        TextView date;
        RecyclerView recyclerView;
        ImageButton expand_button;
        CardView cardview_diary;
        View blue_line;


        public MyViewHolder(@NonNull View itemView) {
            super(itemView);
            tv_day = itemView.findViewById(R.id.day);
            date = itemView.findViewById(R.id.tv_date);

            // Find Recycler View Object
            recyclerView = itemView.findViewById(R.id.recycler_view_diary);
            expand_button = itemView.findViewById(R.id.expand_button);
            cardview_diary = itemView.findViewById(R.id.cardview_viewpager);
            blue_line = itemView.findViewById(R.id.blue_line);
        }
    }
}
