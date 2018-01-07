extern crate nalgebra as na;
extern crate num_complex;
extern crate ncollide;
extern crate nphysics2d;
extern crate nphysics_testbed2d;
#[macro_use]
extern crate lazy_static;
#[macro_use]
extern crate assert_approx_eq;
extern crate rand;
#[macro_use]
extern crate derive_new;
extern crate itertools;
extern crate parallelism;

use na::{Vector1, Vector2, Translation2, Point3, Isometry2, Point2, Point1, Unit, Rotation2};
use ncollide::shape::{Plane, Cuboid};
use ncollide::narrow_phase::ProximityHandler;
use ncollide::query::Proximity;
use nphysics2d::world::{World, WorldCollisionObject};
use nphysics2d::object::{RigidBody, WorldObject, RigidBodyCollisionGroups};
use nphysics_testbed2d::{Testbed, CallBackMode, CallBackId, STEP_RATE};

use std::rc::Rc;
use std::cell::RefCell;

#[macro_use]
mod util;
mod robot;
mod module;
#[cfg(test)]
mod tests;

use robot::Robot;
use module::{RandModule, EnvModule};

fn main() {

    let mut testbed = Testbed::new_empty();

    let mut world = World::new();

    let mut collide_with_sensors = RigidBodyCollisionGroups::new_static();
    collide_with_sensors.enable_interaction_with_sensors();

    let mut walls = Vec::new();

    /*
    *  Set wall planes.
    */

    let room_height = 10.0;
    let room_width = 20.0;
    let room_half_height = room_height/2.;
    let room_half_width = room_width/2.;
    
    let mut top = RigidBody::new_static(Plane::new(Vector2::new(0.0, 1.0)), 0.3, 0.6);
    top.append_translation(&Translation2::new(0.0, -room_half_height));
    top.set_collision_groups(collide_with_sensors);
    world.add_rigid_body(top);
    walls.push((Point2::new(-room_half_width, -room_half_height), Point2::new(room_half_width, -room_half_height)));

    let mut bottom = RigidBody::new_static(Plane::new(Vector2::new(0.0, -1.0)), 0.3, 0.6);
    bottom.append_translation(&Translation2::new(0.0, room_half_height));
    bottom.set_collision_groups(collide_with_sensors);
    world.add_rigid_body(bottom);
    walls.push((Point2::new(-room_half_width, room_half_height), Point2::new(room_half_width, room_half_height)));

    let mut left = RigidBody::new_static(Plane::new(Vector2::new(1.0, 0.0)), 0.3, 0.6);
    left.append_translation(&Translation2::new(-room_half_width, 0.0));
    left.set_collision_groups(collide_with_sensors);
    world.add_rigid_body(left);
    walls.push((Point2::new(-room_half_width, room_half_height), Point2::new(-room_half_width, -room_half_height)));

    let mut right = RigidBody::new_static(Plane::new(Vector2::new(-1.0, 0.0)), 0.3, 0.6);
    right.append_translation(&Translation2::new(room_half_width, 0.0));
    right.set_collision_groups(collide_with_sensors);
    world.add_rigid_body(right);
    walls.push((Point2::new(room_half_width, room_half_height), Point2::new(room_half_width, -room_half_height)));


    /*
    *  Draw walls.
    */

    let wall_half_width = 0.5;

    let wall_color = Point3::new(0.97, 0.97, 0.97);

    macro_rules! add_wall_drawing {
        ($shape:expr, $t1:expr, $t2:expr, $t3:expr) => {
            let mut rb = RigidBody::new_static($shape, 0.3, 0.6);
            rb.append_translation($t1);
            rb.append_translation($t2);
            rb.append_translation($t3);
            rb.set_deactivation_threshold(None);
            rb.set_collision_groups(collide_with_sensors);
            let rb_handle = world.add_rigid_body(rb);
            testbed.set_rigid_body_color(&rb_handle, wall_color);
        };
    };

    let hor_wall = Cuboid::new(Vector2::new(room_half_width + wall_half_width, wall_half_width));

    add_wall_drawing!(hor_wall.clone(), 
        &Translation2::new(0.0, -room_half_height), 
        &Translation2::new(0.0, -wall_half_width), 
        &Translation2::new(wall_half_width, 0.0)
    );

    add_wall_drawing!(hor_wall, 
        &Translation2::new(0.0, room_half_height), 
        &Translation2::new(0.0, wall_half_width), 
        &Translation2::new(-wall_half_width, 0.0)
    );

    let vert_wall = Cuboid::new(Vector2::new(wall_half_width, room_half_height + wall_half_width));

    add_wall_drawing!(vert_wall.clone(), 
        &Translation2::new(-room_half_width, 0.0), 
        &Translation2::new(-wall_half_width, 0.0), 
        &Translation2::new(0.0, -wall_half_width)
    );

    add_wall_drawing!(vert_wall, 
        &Translation2::new(room_half_width, 0.0), 
        &Translation2::new(wall_half_width, 0.0), 
        &Translation2::new(0.0, wall_half_width)
    );

    /*
    *  Make robot
    */

    let module = RandModule::new();
    //Creates the robot and adds it to the world
    let mut robot = Robot::new(module, &mut world, walls.into_boxed_slice());
    testbed.set_rigid_body_color(robot.hardware().rigid_body(), Point3::new(1., 1., 0.)); //(0., 1., 1.) is cool too!
    // testbed.set_sensor_color(robot.hardware().left_hit_sensor(), Point3::new(1., 0., 0.));
    // testbed.set_sensor_color(robot.hardware().right_hit_sensor(), Point3::new(1., 0., 0.));
    testbed.set_sensor_color(&robot.hardware().front_dis_sensor().0, Point3::new(1., 0., 0.));
    testbed.set_sensor_color(&robot.hardware().front_dis_sensor().1, Point3::new(1., 0., 0.));
    testbed.set_sensor_color(&robot.hardware().left_dis_sensor().0, Point3::new(1., 0., 0.));
    testbed.set_sensor_color(&robot.hardware().left_dis_sensor().1, Point3::new(1., 0., 0.));
    testbed.set_sensor_color(&robot.hardware().right_dis_sensor().0, Point3::new(1., 0., 0.));
    testbed.set_sensor_color(&robot.hardware().right_dis_sensor().1, Point3::new(1., 0., 0.));

    let mut x = RigidBody::new_dynamic(Cuboid::new(Vector2::new(0.2,0.2)), 100., 0.3, 0.6);
    x.append_translation(&Translation2::new(room_half_width/2., 0.0));
    // x.set_collision_groups(collide_with_sensors);
    x.set_deactivation_threshold(None);
    let x_handle = world.add_rigid_body(x);

    let dynamic_objects = [robot.hardware().rigid_body().clone()];
    let dynamic_objects2 = dynamic_objects.clone();

    /*
    *  Make testbed and start running
    */

    testbed.set_world(world);

    testbed.add_callback(CallBackId::Cb1, Box::new(
            move | mode: CallBackMode | {
                match mode {
                    CallBackMode::StateActivated => {
                        println!("Robot activated.");
                    },
                    CallBackMode::StateDeactivated => {
                        println!("Robot deactivated.");
                    },
                    CallBackMode::LoopActive => {
                        robot.update(STEP_RATE as f32);
                    },
                    _ => {}
                }
            }
    ));

    let floor_friction_coefficient = 4.;

    testbed.add_callback(CallBackId::Cb2, Box::new(
            move | mode: CallBackMode | {
                match mode {
                    CallBackMode::StateActivated => {
                        println!("Floor friction activated.");
                    },
                    CallBackMode::StateDeactivated => {
                        println!("Floor friction deactivated.");
                    },
                    CallBackMode::LoopActive => {
                        for obj in &dynamic_objects {
                            println!("lin_vel: {}, lin_acc: {}, ang_vel: {}, ang_acc: {}",
                             Point2::from_coordinates(obj.borrow().lin_vel()),
                             Point2::from_coordinates(obj.borrow().lin_acc()),
                             Point1::from_coordinates(obj.borrow().ang_vel()),
                             Point1::from_coordinates(obj.borrow().ang_acc()));

                            let mut obj = obj.borrow_mut();

                            let lin_vel = obj.lin_vel();

                            let deccell_mag = floor_friction_coefficient * obj.mass().unwrap() * 9.81 * (STEP_RATE as f32); // (F(normal) = mg) * coefficient = F(friction)

                            let mut deccell = Rotation2::new(lin_vel[1].atan2(lin_vel[0])) * Vector2::new(deccell_mag, 0.);
                            // println!("predecell: {}, r: {}", Point2::from_coordinates(deccell), lin_vel[1].atan2(lin_vel[0]));


                            if deccell[0].abs() > lin_vel[0].abs() {
                                deccell[0] = lin_vel[0]
                            }
                            if deccell[1].abs() > lin_vel[1].abs() {
                                deccell[1] = lin_vel[1]
                            }

                            let red_lin_vel = lin_vel - deccell;

                            // println!("{}, {}, {}", deccell_mag, Point2::from_coordinates(deccell), Point2::from_coordinates(red_lin_vel));

                            obj.set_lin_vel(red_lin_vel);

                            let ang_vel = obj.ang_vel();

                            let deccel_ang = deccell_mag; // TODO: proper

                            let red_ang_vel = if ang_vel[0].abs() > deccel_ang.abs() {
                                ang_vel + Vector1::new(if ang_vel[0] < 0. {deccel_ang} else {-deccel_ang})
                            } else { Vector1::new(0.) };

                            obj.set_ang_vel(red_ang_vel);
                       }
                    },
                    _ => {}
                }
            }
    ));

    testbed.add_callback(CallBackId::Cb3, Box::new(
            move | mode: CallBackMode | {
                match mode {
                    CallBackMode::StateActivated => {
                        println!("Lin vel anulation activated.");
                    },
                    CallBackMode::StateDeactivated => {
                        println!("Lin vel anulation deactivated.");
                    },
                    CallBackMode::LoopActive => {
                        for obj in &dynamic_objects2 {
                            obj.borrow_mut().set_lin_vel(Vector2::new(0.,0.));
                       }
                    },
                    _ => {}
                }
            }
    ));

    testbed.run();
}