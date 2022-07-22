package org.apache.daffodil.tdml

import javax.xml.bind.JAXBContext
import java.io.FileOutputStream
import cats.effect.IO
import java.io.File
import javax.xml.bind.Marshaller

// import org.typelevel.log4cats.Logger
// import org.typelevel.log4cats.slf4j.Slf4jLogger

// TODO: Put TDML path in class definition?
object TDML {
  // implicit val logger: Logger[IO] = Slf4jLogger.getLogger
  // Create a ParserTestCaseType object that can be put into a TestSuite
  // These types are generated when JAXB is executed on the TDML schema
  // 
  // The format of the new ParserTestCase is as follows:
  // 
  // <tdml:parserTestCase name="$tdmlName" root="file" model="$schemaPath" description="$tdmlDescription" roundTrip="onePass">
  //   <tdml:document>
  //     <tdml:documentPart type="file">$dataPath</tdml:documentPart>
  //   <tdml:document>
  //   <tdml:infoset>
  //     <tdml:dfdlInfoset type="file">$infosetPath</tdml:dfdlInfoset>
  //   </tdml:infoset>
  // </tdml:parserTestCase>
  // 
  // infosetPath:     Path to the infoset
  // dataPath:        Path to the data file
  // schemaPath:      Path to the DFDL Schema
  // tdmlName:        Name of the DFDL operation
  // tdmlDescription: Description for the DFDL operation
  // 
  // Returns the ParserTestCase object created with the applied paths
  def createTestCase(infosetPath: String, dataPath: String, schemaPath: String, tdmlName: String, tdmlDescription: String): ParserTestCaseType = {
    val factory = new ObjectFactory()

    val dfdlInfoset = factory.createDfdlInfosetType()
    dfdlInfoset.setType("file")
    dfdlInfoset.getContent().add(infosetPath)

    val infoset = factory.createInfosetType()
    infoset.setDfdlInfoset(dfdlInfoset)

    val docPart = factory.createDocumentPartType()
    docPart.setType(DocumentPartTypeEnum.FILE)
    docPart.setValue(dataPath)

    val doc = factory.createDocumentType()
    // The following line causes the output of the marshalling to be empty
    doc.getContent().add(docPart)

    val testCase = factory.createParserTestCaseType()
    testCase.setName(tdmlName)
    testCase.setRoot("file")
    testCase.setModel(schemaPath)
    testCase.setDescription(tdmlDescription)
    testCase.setRoundTrip(RoundTripType.ONE_PASS)
    testCase.getTutorialOrDocumentOrInfoset().add(doc)
    testCase.getTutorialOrDocumentOrInfoset().add(infoset)

    return testCase
  }

  // Generate a new TDML file.
  // There is a suiteName attribute in the root element (TestSuite) of the document. This is set to $tdmlName
  // Paths given to this function should be relative as it should be expected for the TDML files to be shared on the mailing list
  //
  // infosetPath:     Path to the infoset
  // dataPath:        Path to the data file
  // schemaPath:      Path to the DFDL Schema
  // tdmlName:        Name of the DFDL operation
  // tdmlDescription: Description for the DFDL operation
  // tdmlPath:        Path to the TDML file
  // 
  // There is a suiteName attribute in the root element of the document. This is set to $tdmlName
  // TODO: I think the return type here should just be Unit
  def generate(infosetPath: String, dataPath: String, schemaPath: String, tdmlName: String, tdmlDescription: String, tdmlPath: String): Unit = {
    // Logger[IO].debug("Generating")
    val factory = new ObjectFactory()

    val testSuite = factory.createTestSuite()
    testSuite.setSuiteName(tdmlName)
    testSuite.setDefaultRoundTrip(RoundTripType.ONE_PASS)
    testSuite.getTutorialOrParserTestCaseOrDefineSchema().add(createTestCase(infosetPath, dataPath, schemaPath, tdmlName, tdmlDescription))

    // Logger[IO].debug("Getting ready to send XML to file")
    // val marshaller = JAXBContext.newInstance(classOf[TestSuite]).createMarshaller()
    // marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
    // marshaller.marshal(testSuite, new FileOutputStream(tdmlPath))
    // JAXBContext.newInstance(classOf[TestSuite]).createMarshaller().marshal(testSuite, fos)
    // val fos = new FileOutputStream(tdmlPath)
    val marshaller = JAXBContext.newInstance(classOf[TestSuite]).createMarshaller()
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
    marshaller.marshal(testSuite, new java.io.File(tdmlPath))
  }

  // Append a new test case to an existing TDML file.
  // Paths given to this function should be relative as it should be expected for the TDML files to be shared on the mailing list
  // 
  // infosetPath:     Path to the infoset
  // dataPath:        Path to the data file
  // schemaPath:      Path to the DFDL Schema
  // tdmlName:        Name of the DFDL operation
  // tdmlDescription: Description for the DFDL operation
  // tdmlPath:        Path to the TDML file
  // 
  // TODO: I think the return type here should just be Unit
  def append(infosetPath: String, dataPath: String, schemaPath: String, tdmlName: String, tdmlDescription: String, tdmlPath: String): IO[Unit] = {

    val testSuite = JAXBContext.newInstance(classOf[TestSuite]).createUnmarshaller().unmarshal(new File(tdmlPath)).asInstanceOf[TestSuite]

    testSuite.getTutorialOrParserTestCaseOrDefineSchema().add(createTestCase(infosetPath, dataPath, schemaPath, tdmlName, tdmlDescription))

    IO(JAXBContext.newInstance(classOf[TestSuite]).createMarshaller().marshal(testSuite, new FileOutputStream(tdmlPath)))
  }

  // Find the parameters needed to execute a DFDL parse based on the given TDML Parameters
  // 
  // tdmlName:        Test case name to run
  // tdmlDescription: Description of test case to run
  // tdmlPath:        File path of TDML file to extract test case from
  // 
  // Returns a tuple containing the following (Path to DFDL Schema, Path to Data File)
  // All paths returned could be either relative or absolute - it depends on what exists in the TDML file
  def execute(tdmlName: String, tdmlDescription: String, tdmlPath: String): (String, String) = {
    val testCaseList = JAXBContext.newInstance(classOf[TestSuite]).createUnmarshaller().unmarshal(new File(tdmlPath)).asInstanceOf[TestSuite].getTutorialOrParserTestCaseOrDefineSchema()

    testCaseList.forEach { tc =>
      // var foundDoc = ""
      // var foundInfoset = ""

      // TODO: Do I really have to cast to instances every time? I've already checked that they are...
      tc match {
        case ptc: ParserTestCaseType =>
          if (ptc.getName() == tdmlName && ptc.getDescription() == tdmlDescription) {
            ptc.getTutorialOrDocumentOrInfoset().forEach { dis =>
              dis match {
                case doc: DocumentType =>
                  return (ptc.getModel(), doc.getContent().indexOf(0).asInstanceOf[DocumentPartType].getValue())
              }
            }
          }
      }
      /*if (tc.isInstanceOf[ParserTestCaseType]) {
        // Match name and description of potential test case
        if (tc.asInstanceOf[ParserTestCaseType].getName() == tdmlName && tc.asInstanceOf[ParserTestCaseType].getDescription() == tdmlDescription) {
          tc.asInstanceOf[ParserTestCaseType].getTutorialOrDocumentOrInfoset().forEach { dis =>
            if (dis.isInstanceOf[DocumentType]) {
              // foundDoc = dis.asInstanceOf[DocumentType].getContent().indexOf(0).asInstanceOf[DocumentPartType].getValue()
              // if (!foundInfoset.isEmpty()) {
                // return (tc.asInstanceOf[ParserTestCaseType].getModel(), foundInfoset, foundDoc)
              // }
              return (tc.asInstanceOf[ParserTestCaseType].getModel(), dis.asInstanceOf[DocumentType].getContent().indexOf(0).asInstanceOf[DocumentPartType].getValue())
            }
            // else if (dis.isInstanceOf[InfosetType]) {
              // foundInfoset = dis.asInstanceOf[InfosetType].getDfdlInfoset().getContent().indexOf(0).asInstanceOf[String]
              // if (!foundDoc.isEmpty()) {
                // return (tc.asInstanceOf[ParserTestCaseType].getModel(), foundInfoset, foundDoc)
              // }
            // }
          }
        }
      }*/
    }

    // If there is no test case in the TDML file meeting the name/description criteria, return empty
    ("", "")
  }
}
