#!/usr/bin/env python
"""
RenderDoc Launcher Script for Fun Engine

This script launches RenderDoc with the Fun Engine Java application.
It automatically detects the RenderDoc installation and runs the application
with graphics debugging enabled.

Usage:
    python run_renderdoc.py [options]

Options:
    --renderdoc-path PATH                      Specify a custom path to the RenderDoc executable
    --working-dir DIR                          Set the working directory of the program
    --capture-file TEMPLATE                    Set the filename template for new captures
    --wait-for-exit                            Wait for the target program to exit
    --disallow-vsync                           Disallow the application from enabling vsync
    --disallow-fullscreen                      Disallow the application from enabling fullscreen
    --api-validation                           Record API debugging events and messages
    --capture-callstacks                       Capture CPU callstacks for API events
    --ref-all-resources                        Include all live resources, not just those used by a frame

Requirements:
    - RenderDoc must be installed on the system
    - The Fun Engine project must be built (run './gradlew build' first)
"""

import os
import subprocess
import sys
import argparse

def parse_args():
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(description="Launch RenderDoc with Fun Engine")
    parser.add_argument("--renderdoc-path", help="Custom path to RenderDoc executable")
    parser.add_argument("--working-dir", help="Set the working directory of the program")
    parser.add_argument("--capture-file", help="Set the filename template for new captures")
    parser.add_argument("--wait-for-exit", action="store_true", help="Wait for the target program to exit")
    parser.add_argument("--disallow-vsync", action="store_true", help="Disallow the application from enabling vsync")
    parser.add_argument("--disallow-fullscreen", action="store_true", help="Disallow the application from enabling fullscreen")
    parser.add_argument("--api-validation", action="store_true", help="Record API debugging events and messages")
    parser.add_argument("--capture-callstacks", action="store_true", help="Capture CPU callstacks for API events")
    parser.add_argument("--ref-all-resources", action="store_true", help="Include all live resources, not just those used by a frame")
    return parser.parse_args()

def main():
    # Parse command-line arguments
    args = parse_args()

    # Use custom RenderDoc path if provided
    if args.renderdoc_path:
        if os.path.exists(args.renderdoc_path):
            renderdoc_path = args.renderdoc_path
        else:
            print(f"Error: Custom RenderDoc path not found: {args.renderdoc_path}")
            sys.exit(1)
    else:
        # Path to RenderDoc executable - auto-detect based on platform
        renderdoc_paths = []

        # Windows paths
        if sys.platform.startswith('win'):
            renderdoc_paths = [
                r"C:\Program Files\RenderDoc\renderdoccmd.exe",  # Default Windows installation
                r"C:\Program Files (x86)\RenderDoc\renderdoccmd.exe",  # Alternative Windows installation
            ]
        # macOS paths
        elif sys.platform == 'darwin':
            renderdoc_paths = [
                "/Applications/RenderDoc.app/Contents/MacOS/renderdoccmd",
            ]
        # Linux paths
        elif sys.platform.startswith('linux'):
            renderdoc_paths = [
                "/usr/bin/renderdoccmd",
                "/usr/local/bin/renderdoccmd",
                os.path.expanduser("~/.local/bin/renderdoccmd"),
            ]

        renderdoc_path = None
        for path in renderdoc_paths:
            if os.path.exists(path):
                renderdoc_path = path
                break

        if not renderdoc_path:
            print("Error: RenderDoc executable not found. Please install RenderDoc or specify the path with --renderdoc-path.")
            sys.exit(1)

    # Path to the Java application JAR file
    jar_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "libs", "Fun-1.0-SNAPSHOT-fat.jar")

    # Check if the JAR file exists
    if not os.path.exists(jar_path):
        print(f"Error: JAR file not found at {jar_path}")
        print("Make sure you have built the project with './gradlew build' before running this script.")
        sys.exit(1)

    # Command to run the Java application as specified in the requirements
    java_command = ["java -jar ", jar_path]

    # Launch RenderDoc with the Java application
    try:
        # Build the command with the appropriate options
        cmd = [renderdoc_path, "capture"]

        # Add optional arguments if specified
        if args.working_dir:
            cmd.extend(["--working-dir", args.working_dir])
        if args.capture_file:
            cmd.extend(["--capture-file", args.capture_file])
        if args.wait_for_exit:
            cmd.append("--wait-for-exit")
        if args.disallow_vsync:
            cmd.append("--opt-disallow-vsync")
        if args.disallow_fullscreen:
            cmd.append("--opt-disallow-fullscreen")
        if args.api_validation:
            cmd.append("--opt-api-validation")
        if args.capture_callstacks:
            cmd.append("--opt-capture-callstacks")
        if args.ref_all_resources:
            cmd.append("--opt-ref-all-resources")

        # Add the Java command
        cmd.extend(java_command)

        # Display the command being executed
        print(f"Launching RenderDoc with command: {' '.join(cmd)}")

        cmd = "\"C:/Program Files/RenderDoc/renderdoccmd.exe\" capture java -jar  C:/Users/natan/Desktop/Fun/build/libs/Fun-1.0-SNAPSHOT-fat.jar"

        # Run the command
        subprocess.run(cmd)
    except Exception as e:
        print(f"Error launching RenderDoc: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
