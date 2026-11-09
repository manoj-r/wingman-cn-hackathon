import RegistrationForm from "../../../../components/RegistrationForm";
export default async function Register({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <RegistrationForm conferenceId={id} />;
}
