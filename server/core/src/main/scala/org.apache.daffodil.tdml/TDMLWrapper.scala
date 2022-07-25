package org.apache.daffodil.tdml

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object TDMLWrapper {
  // Convert an absolute path into a path relative to the current working directory
  // 
  // path: Absolute path to convert into a relative path
  // 
  // Returns the relative path. Note that this path is given as a string.
  private def convertToRelativePath(path: Path): String = {
    // Don't forget the getParent because toAbsolutePath returns something like the following:
    //   /absolute/path/to/directory/.
    // The getParent gets rid of the dot at the end.
    var workingDir = Paths.get(".").toAbsolutePath().getParent()
    var prefix = ""

    // This is used to back up the path tree in order to find the first common ancestor of both paths
    // If a user wants to use a file not in or under the current working directory, this will be required to
    //   produce the expected output.
    // A possible use case of this is where a user has a data folder and a schema folder that are siblings.
    while (!path.startsWith(workingDir) && Paths.get(workingDir.toString()).getParent() != null)
    {
      workingDir = Paths.get(workingDir.toString()).getParent()
      // Need to add the dots to represent that we've gone back a step up the path
      prefix += ".." + File.separator
    }

    return prefix + new File(workingDir.toString()).toURI().relativize(new File(path.toString()).toURI()).getPath().toString()
  }

  // Generate a new TDML file.
  // Paths given to this function should be absolute as they will be converted to relative paths
  //
  // infosetPath:     Path to the infoset
  // dataPath:        Path to the data file
  // schemaPath:      Path to the DFDL Schema
  // tdmlName:        Name of the DFDL operation
  // tdmlDescription: Description for the DFDL operation
  // tdmlPath:        Path to the TDML file
  def generate(infosetPath: Path, dataPath: Path, schemaPath: Path, tdmlName: String, tdmlDescription: String, tdmlPath: String): Unit = {
    TDML.generate(convertToRelativePath(infosetPath), convertToRelativePath(dataPath), convertToRelativePath(schemaPath), tdmlName, tdmlDescription, tdmlPath)
  }

  // Append a new test case to an existing TDML file.
  // Paths given to this function should be absolute as they will be converted to relative paths
  // 
  // infosetPath:     Path to the infoset
  // dataPath:        Path to the data file
  // schemaPath:      Path to the DFDL Schema
  // tdmlName:        Name of the DFDL operation
  // tdmlDescription: Description for the DFDL operation
  // tdmlPath:        Path to the TDML file
  def append(infosetPath: Path, dataPath: Path, schemaPath: Path, tdmlName: String, tdmlDescription: String, tdmlPath: String): Unit = {
    TDML.append(convertToRelativePath(infosetPath), convertToRelativePath(dataPath), convertToRelativePath(schemaPath), tdmlName, tdmlDescription, tdmlPath)
  }

  // Find the parameters needed to execute a DFDL parse based on the given TDML Parameters
  // 
  // tdmlName:        Test case name to run
  // tdmlDescription: Description of test case to run
  // tdmlPath:        File path of TDML file to extract test case from
  // 
  // Returns a tuple containing the following (Path to DFDL Schema, Path to Data File)
  // All paths returned could be either relative or absolute - it depends on what exists in the TDML file
  def execute(schemaPath: Path, dataPath: Path, tdmlName: String, tdmlDescription: String, tdmlPath: String): (Path, Path) = {
    val (newSchemaPath, newDataPath) = TDML.execute(tdmlName, tdmlDescription, tdmlPath)
    if (newSchemaPath.length > 0 && newDataPath.length > 0) {
      return (Paths.get(newSchemaPath), Paths.get(newDataPath))
    }

    return (schemaPath, dataPath)
  }
}
