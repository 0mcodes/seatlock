package com.omcodes.seatlock.venue;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name="name",nullable = false,length = 200)
    private String name;

    @Column(name = "city",nullable = false,length = 100)
    private String city;

    @Column(name = "address",length = 300)
    private String address;

    public Venue(String name, String city, String address){
        this.name = name;
        this.city = city;
        this.address = address;
    }
}
