package com.example.chleh.smart_pillow;

public class Alram_Infor {
    private int year;
    private int month;
    private int day;
    private int hour;
    private int min;
    private int id;
    private int week_order_day;
    private boolean pattern[] =new boolean [8];
    private boolean redo;
        Alram_Infor(int year, int month, int day, int week_first_day, int hour, int min, boolean pattern [], int id)
       {
           for(int i=1;i<8;i++)
           {
               this.pattern[i]=false;
           }
           setYear(year);
           setMonth(month);
           setDay(day);
           setDayOrder(week_first_day);
           setHour(hour);
           setMin(min);
           setPattern(pattern);
           setId(id);
           setredo(pattern);
       }
     void setredo(boolean [] pattern) {
         redo = false;
         for (int i = 1; i < 8; i++) {
             if (pattern[i]) {
                 redo = true;
             }
         }
     }
    boolean get_redo(){return redo;}
    void setId(int id){this.id=id;}
    void setYear(int year)
    {
        this.year=year;
    }
    void setMonth(int month){this.month=month;}
    void setDay(int day)
    {
        this.day=day;
    }
    void setHour(int hour)
    {
        this.hour=hour;
    }
    void setMin(int min)
    {
        this.min=min;
    }
    void setDayOrder(int week_first_day)
    {
        this.week_order_day=week_first_day;;
    }
    int getId()
    {
        return this.id;
    }
    int getYear()
    {
        return this.year;
    }
    int getMonth()
    {
        return this.month;
    }
    int getDay()
    {
        return this.day;
    }
    int getMin()
    {
        return this.min;
    }
    int getHour()
    {
        return this.hour;
    }
    int getOrderDay()
    {
        return this.week_order_day;
    }
    void setPattern(boolean pattern[])
    {
        for(int i=1;i<8;i++)
        this.pattern[i]=pattern[i];
        this.setredo(this.pattern);
    }
    void setdaypattern(int i)
    {
        this.pattern[i]=true;
        this.setredo(this.pattern);
    }
    boolean  getPattern(int a)
    {
        return this.pattern[a];
    }
}
